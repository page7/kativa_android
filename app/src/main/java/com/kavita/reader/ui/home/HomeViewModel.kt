package com.kavita.reader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavita.reader.core.image.CoverUrlBuilder
import com.kavita.reader.core.image.PrivacyModeState
import com.kavita.reader.core.network.KavitaError
import com.kavita.reader.core.network.Outcome
import com.kavita.reader.data.remote.dto.GroupedSeriesDto
import com.kavita.reader.data.remote.dto.SeriesDto
import com.kavita.reader.data.repository.LibraryRepository
import com.kavita.reader.data.repository.SeriesRepository
import com.kavita.reader.domain.model.SeriesFilter
import com.kavita.reader.domain.model.SeriesSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One rendered row on the home screen. */
sealed interface HomeStream {
    val key: String
    val titleRes: Int

    data class OnDeck(val items: List<SeriesDto>) : HomeStream {
        override val key = "on-deck"
        override val titleRes = com.kavita.reader.R.string.home_stream_on_deck
    }

    data class RecentlyUpdated(val items: List<GroupedSeriesDto>) : HomeStream {
        override val key = "recently-updated"
        override val titleRes = com.kavita.reader.R.string.home_stream_recently_updated
    }

    data class RecentlyAdded(val items: List<SeriesDto>) : HomeStream {
        override val key = "recently-added"
        override val titleRes = com.kavita.reader.R.string.home_stream_recently_added
    }
}

/**
 * A library rendered as a folder tile on the home screen.
 *
 * [previewCovers] holds up to 4 covers in the library's default sort order, which is what makes
 * the tile recognisable at a glance without opening it.
 */
data class HomeLibraryFolder(
    val libraryId: Int,
    val name: String,
    val seriesCount: Int,
    val previewCovers: List<String?>,
    val libraryType: Int = 0,
)

data class HomeUiState(
    val loading: Boolean = true,
    /** Pull-to-refresh in flight; the streams stay visible instead of being replaced. */
    val refreshing: Boolean = false,
    val privacyMode: Boolean = false,
    val streams: List<HomeStream> = emptyList(),
    val folders: List<HomeLibraryFolder> = emptyList(),
    val error: KavitaError? = null,
    val onDeckCount: Int = 0,
) {
    val isEmpty: Boolean
        get() = !loading && error == null && streams.isEmpty() && folders.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val seriesRepository: SeriesRepository,
    private val libraryRepository: LibraryRepository,
    private val coverUrlBuilder: CoverUrlBuilder,
    private val privacyModeState: PrivacyModeState,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        observePrivacy()
        load()
    }

    private fun observePrivacy() {
        viewModelScope.launch {
            privacyModeState.enabled.collect { enabled ->
                _state.update { it.copy(privacyMode = enabled) }
            }
        }
    }

    fun setPrivacyMode(enabled: Boolean) {
        privacyModeState.toggleForActiveServer(enabled)
        _state.update { it.copy(privacyMode = enabled) }
    }

    fun load(showSpinner: Boolean = true) {
        _state.update { it.copy(loading = showSpinner, error = null) }

        viewModelScope.launch {
            coroutineScope {
                // Streams and folders are independent: one slow call must not blank the page.
                // Each row is a horizontal peek, not a browsing surface: ten entries is what the
                // width of a phone can hint at, and "View all" is one tap away. Ask the server for
                // ten instead of trimming client-side so no extra payload is transferred.
                val onDeck = async { seriesRepository.onDeck(pageSize = STREAM_PAGE_SIZE) }
                val updated = async { seriesRepository.recentlyUpdated(pageSize = STREAM_PAGE_SIZE) }
                val added = async { seriesRepository.recentlyAdded(pageSize = STREAM_PAGE_SIZE) }
                val libraries = async { seriesRepository.libraries() }

                val onDeckResult = onDeck.await()
                val updatedResult = updated.await()
                val addedResult = added.await()
                val libraryResult = libraries.await()

                val streams = buildList {
                    // The "continue reading" hero was removed by product decision: the top of the
                    // home screen must start with the streams (docs/PAGES.md P03). On Deck already
                    // carries the continue-reading entries inside its own row.
                    //
                    // `take` is a belt-and-braces guard on top of `pageSize`: a server that ignores
                    // the parameter (or one with no pagination at all) must still not render an
                    // endless row on the home screen.
                    (onDeckResult as? Outcome.Success)?.value?.take(STREAM_PAGE_SIZE)
                        ?.takeIf { it.isNotEmpty() }?.let {
                            add(HomeStream.OnDeck(it))
                        }
                    (updatedResult as? Outcome.Success)?.value?.take(STREAM_PAGE_SIZE)
                        ?.takeIf { it.isNotEmpty() }?.let {
                            add(HomeStream.RecentlyUpdated(it))
                        }
                    (addedResult as? Outcome.Success)?.value?.take(STREAM_PAGE_SIZE)
                        ?.takeIf { it.isNotEmpty() }?.let {
                            add(HomeStream.RecentlyAdded(it))
                        }
                }

                val folders = (libraryResult as? Outcome.Success)?.value
                    ?.map { dto: com.kavita.reader.data.remote.api.LibraryDto ->
                        // Preview covers come from the library's own default ordering. The tile's
                        // count must be the server's grand total, NOT the number of previews:
                        // `series.size` is capped at PREVIEW_COVER_COUNT, which made every folder
                        // claim "4 series".
                        val preview = libraryRepository.seriesPageInLibrary(
                            libraryId = dto.id,
                            page = 1,
                            pageSize = PREVIEW_COVER_COUNT,
                            filter = SeriesFilter.Default,
                        )
                        val page = (preview as? Outcome.Success)?.value
                        val series = page?.items.orEmpty()
                        HomeLibraryFolder(
                            libraryId = dto.id,
                            name = dto.name,
                            seriesCount = page?.totalOrPageSize ?: 0,
                            previewCovers = series.take(PREVIEW_COVER_COUNT)
                                .map { coverUrlBuilder.series(it.id) },
                            libraryType = dto.type,
                        )
                    }
                    .orEmpty()

                val failures = listOf(onDeckResult, updatedResult, addedResult, libraryResult)
                    .filterIsInstance<Outcome.Failure>()

                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        streams = streams,
                        folders = folders,
                        // Only report an error when there is nothing at all to show.
                        error = if (streams.isEmpty() && folders.isEmpty()) {
                            failures.firstOrNull()?.failure?.error
                        } else {
                            null
                        },
                        onDeckCount = ((onDeckResult as? Outcome.Success)?.value?.size ?: 0)
                            .coerceAtMost(STREAM_PAGE_SIZE),
                    )
                }
            }
        }
    }

    /**
     * Pull-to-refresh.
     *
     * Keeps [HomeUiState.loading] false: the whole point is to refresh in place, so replacing the
     * streams with a centred spinner would throw away the user's scroll position and make the pull
     * feel like a page reload.
     */
    fun refresh() {
        if (_state.value.refreshing || _state.value.loading) return
        _state.update { it.copy(refreshing = true, error = null) }
        load(showSpinner = false)
    }

    /**
     * Cover URL for a series row.
     *
     * Deliberately returns the URL even in privacy mode: [com.kavita.reader.core.designsystem.CoverImage]
     * is what decides whether a request is built, so there is exactly one place to get this right.
     * Blanking the URL here instead was the source of the "home is protected but nothing below it
     * is" bug — the two halves disagreed and only home was covered.
     */
    fun seriesCover(seriesId: Int): String? = coverUrlBuilder.series(seriesId)

    private companion object {
        const val PREVIEW_COVER_COUNT = 4

        /**
         * Entries per home stream row.
         *
         * Product decision: the home rows are a *peek*, so each shows at most ten series. The full
         * list lives behind "View all" (docs/PAGES.md P03).
         */
        const val STREAM_PAGE_SIZE = 10
    }
}
