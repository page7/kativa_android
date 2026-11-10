package com.kavita.reader.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavita.reader.core.image.CoverUrlBuilder
import com.kavita.reader.core.image.PrivacyModeState
import com.kavita.reader.core.network.KavitaError
import com.kavita.reader.core.network.Outcome
import com.kavita.reader.data.repository.LibraryRepository
import com.kavita.reader.domain.model.SeriesFilter
import com.kavita.reader.domain.model.SeriesSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryGridItem(
    val series: SeriesSummary,
    val coverUrl: String?,
)

data class LibraryGridUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    /** Pull-to-refresh in flight. Distinct from [loading] so content stays on screen. */
    val refreshing: Boolean = false,
    val items: List<LibraryGridItem> = emptyList(),
    val page: Int = 1,
    val endReached: Boolean = false,
    val error: KavitaError? = null,
    val privacyMode: Boolean = false,
    val filter: SeriesFilter = SeriesFilter.Default,
) {
    val isEmpty: Boolean get() = !loading && error == null && items.isEmpty()

    /** True when the grid is empty *because* a filter is active. */
    val isEmptyDueToFilter: Boolean get() = isEmpty && !filter.isEmpty

    /** Drives the tinted sort/filter action, so an active condition is visible at a glance. */
    val hasActiveFilter: Boolean get() = !filter.isEmpty
}

@HiltViewModel
class LibraryGridViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val coverUrlBuilder: CoverUrlBuilder,
    private val privacyModeState: com.kavita.reader.core.image.PrivacyModeState,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val libraryId: Int = savedStateHandle.get<String>("libraryId")?.toIntOrNull() ?: 0

    private val _state = MutableStateFlow(LibraryGridUiState())
    val state: StateFlow<LibraryGridUiState> = _state.asStateFlow()

    /** Guards against a slow page landing after the user already changed the filter. */
    private var requestId = 0

    init {
        observePrivacy()
        load(reset = true)
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

    fun load(reset: Boolean = false, showSpinner: Boolean = reset, forceRefresh: Boolean = false) {
        val current = _state.value
        val nextPage = if (reset) 1 else current.page + 1
        val request = ++requestId
        _state.update {
            it.copy(
                loading = showSpinner,
                loadingMore = !reset && showSpinner,
                error = null,
                page = nextPage,
            )
        }

        viewModelScope.launch {
            when (
                val outcome = libraryRepository.seriesPageInLibrary(
                    libraryId = libraryId,
                    page = nextPage,
                    pageSize = PAGE_SIZE,
                    filter = current.filter,
                    forceRefresh = forceRefresh,
                )
            ) {
                is Outcome.Success -> {
                    // A slow response for an older filter must not overwrite a newer one.
                    if (request != requestId) return@launch
                    val page = outcome.value
                    val newItems = page.items.map { series ->
                        LibraryGridItem(
                            series = series,
                            coverUrl = coverUrlBuilder.series(series.id),
                        )
                    }
                    _state.update {
                        val merged = if (reset) newItems else it.items + newItems
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            refreshing = false,
                            items = merged,
                            endReached = isLastPage(nextPage, page),
                        )
                    }
                }

                is Outcome.Failure -> {
                    if (request != requestId) return@launch
                    _state.update {
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
    }

    /**
     * Pull-to-refresh.
     *
     * Deliberately does not raise [LibraryGridUiState.loading]: the spinner belongs to the pull
     * gesture, so the current grid stays on screen while the request is in flight. Raising the
     * full-screen spinner here made every pull blank the page and lose the scroll position.
     *
     * [forceRefresh] bypasses the page cache, otherwise the pull would spin and hand back the same
     * rows.
     */
    fun refresh() {
        val s = _state.value
        if (s.refreshing || s.loading) return
        _state.update { it.copy(refreshing = true, error = null) }
        load(reset = true, showSpinner = false, forceRefresh = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached) return
        load(reset = false)
    }

    // --- sorting / filtering --------------------------------------------

    fun applyFilter(filter: SeriesFilter) {
        _state.update { it.copy(filter = filter) }
        // Filter changes must not come from cache: the signature includes the filter, so a cached
        // page would be a *previous* run of the same filter, not the live data.
        load(reset = true, forceRefresh = true)
    }

    fun clearFilter() {
        val current = _state.value.filter
        applyFilter(SeriesFilter(sort = current.sort, ascending = current.ascending))
    }

    private companion object {
        const val PAGE_SIZE = 40

        /**
         * End-of-list detection.
         *
         * The `Pagination` header carries the true total, so this is exact when it is present.
         * When the header is missing (`totalItems == 0`, see `LibraryRepository.totalFrom`) the
         * old "short page" heuristic is the only signal left.
         */
        fun isLastPage(page: Int, response: com.kavita.reader.data.repository.SeriesPage): Boolean {
            val total = response.totalItems
            return if (total > 0) {
                page * PAGE_SIZE >= total
            } else {
                response.items.size < PAGE_SIZE
            }
        }
    }
}
