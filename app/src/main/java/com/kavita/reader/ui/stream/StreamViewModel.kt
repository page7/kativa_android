package com.kavita.reader.ui.stream

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavita.reader.core.auth.ActiveServerStore
import com.kavita.reader.core.image.CoverUrlBuilder
import com.kavita.reader.core.image.PrivacyModeState
import com.kavita.reader.core.network.KavitaError
import com.kavita.reader.core.network.Outcome
import com.kavita.reader.core.network.map
import com.kavita.reader.data.repository.LibraryRepository
import com.kavita.reader.data.repository.SeriesRepository
import com.kavita.reader.domain.model.SeriesFilter
import com.kavita.reader.domain.model.SeriesSort
import com.kavita.reader.domain.model.SeriesSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which home stream the "view all" screen is expanding. */
enum class StreamKind(val routeKey: String) {
    OnDeck("on-deck"),
    RecentlyUpdated("recently-updated"),
    RecentlyAdded("recently-added"),
    ;

    val titleRes: Int
        get() = when (this) {
            OnDeck -> com.kavita.reader.R.string.home_stream_on_deck
            RecentlyUpdated -> com.kavita.reader.R.string.home_stream_recently_updated
            RecentlyAdded -> com.kavita.reader.R.string.home_stream_recently_added
        }

    companion object {
        fun from(key: String?): StreamKind = entries.firstOrNull { it.routeKey == key } ?: OnDeck
    }
}

data class StreamItem(val series: SeriesSummary, val coverUrl: String?)

/**
 * Stable identity for list keys.
 *
 * `RecentlyUpdated` is grouped per *chapter*, so the same series legitimately appears on several
 * pages. Using `series.id` as the Compose key therefore produced duplicate keys and crashed the
 * grid once the user scrolled far enough. The key is now the list position, which is always unique.
 */
data class StreamEntry(val key: String, val item: StreamItem)

data class StreamUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    /** Pull-to-refresh in flight; keeps the current grid visible instead of blanking it. */
    val refreshing: Boolean = false,
    val entries: List<StreamEntry> = emptyList(),
    val page: Int = 1,
    val endReached: Boolean = false,
    val error: KavitaError? = null,
    val privacyMode: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && error == null && entries.isEmpty()
    val items: List<StreamItem> get() = entries.map { it.item }
}

/**
 * Full-list view for a home stream.
 *
 * Each stream has a different ordering on the server, so rather than re-sorting client-side we
 * keep the stream-specific call and page through it.
 */
@HiltViewModel
class StreamViewModel @Inject constructor(
    private val seriesRepository: SeriesRepository,
    private val libraryRepository: LibraryRepository,
    private val coverUrlBuilder: CoverUrlBuilder,
    private val privacyModeState: PrivacyModeState,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val kind: StreamKind = StreamKind.from(savedStateHandle.get<String>("streamKey"))

    private val _state = MutableStateFlow(StreamUiState())
    val state: StateFlow<StreamUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            privacyModeState.enabled.collect { enabled ->
                _state.update { it.copy(privacyMode = enabled) }
            }
        }
        load(reset = true)
    }

    fun load(reset: Boolean = false, showSpinner: Boolean = reset) {
        val nextPage = if (reset) 1 else _state.value.page + 1
        _state.update {
            it.copy(
                loading = showSpinner,
                loadingMore = !reset,
                error = null,
                page = nextPage,
            )
        }
        viewModelScope.launch {
            val outcome: Outcome<List<SeriesSummary>> = when (kind) {
                // On Deck and Recently Added are already series lists.
                StreamKind.OnDeck -> seriesRepository.onDeck(page = nextPage, pageSize = PAGE_SIZE)
                    .map { dtos -> dtos.map { com.kavita.reader.data.mapper.ContentMapper.series(it) } }

                StreamKind.RecentlyAdded -> libraryRepository.allSeries(
                    page = nextPage,
                    pageSize = PAGE_SIZE,
                    filter = SeriesFilter(sort = SeriesSort.CreatedDate, ascending = false),
                )

                // Recently Updated is grouped by series on the server; its natural order is what
                // the home row shows, so keep that instead of inventing a sort.
                StreamKind.RecentlyUpdated -> seriesRepository.recentlyUpdated(
                    page = nextPage,
                    pageSize = PAGE_SIZE,
                ).map { grouped ->
                    grouped
                        .distinctBy { it.seriesId }
                        .map { g ->
                            SeriesSummary(
                                id = g.seriesId,
                                name = g.localizedSeriesName?.takeIf { it.isNotBlank() } ?: g.seriesName,
                                libraryId = g.libraryId,
                                libraryName = "",
                                format = com.kavita.reader.domain.model.ContentFormat.from(g.format),
                                pages = 0,
                                pagesRead = 0,
                            )
                        }
                }
            }

            when (outcome) {
                is Outcome.Success -> {
                    val newEntries = outcome.value.mapIndexed { index, summary ->
                        StreamEntry(
                            key = "s${nextPage}_$index",
                            item = StreamItem(summary, coverUrlBuilder.series(summary.id)),
                        )
                    }
                    _state.update {
                        val merged = if (reset) newEntries else it.entries + newEntries
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            refreshing = false,
                            entries = merged,
                            endReached = newEntries.size < PAGE_SIZE,
                        )
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        refreshing = false,
                        error = outcome.failure.error,
                    )
                }
            }
        }
    }

    /**
     * Pull-to-refresh.
     *
     * Does not raise [StreamUiState.loading] — that would replace the grid with a spinner and lose
     * the scroll position for what is meant to be a lightweight reload.
     */
    fun refresh() {
        val s = _state.value
        if (s.refreshing || s.loading) return
        _state.update { it.copy(refreshing = true, error = null) }
        load(reset = true, showSpinner = false)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached) return
        load(reset = false)
    }

    fun setPrivacyMode(enabled: Boolean) {
        privacyModeState.toggleForActiveServer(enabled)
        _state.update { it.copy(privacyMode = enabled) }
    }

    private companion object {
        const val PAGE_SIZE = 40
    }
}
