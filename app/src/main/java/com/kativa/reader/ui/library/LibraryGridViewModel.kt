package com.kativa.reader.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.image.CoverUrlBuilder
import com.kativa.reader.core.image.PrivacyModeState
import com.kativa.reader.core.network.KativaError
import com.kativa.reader.core.network.Outcome
import com.kativa.reader.data.repository.LibraryRepository
import com.kativa.reader.domain.model.SeriesFilter
import com.kativa.reader.domain.model.SeriesSort
import com.kativa.reader.domain.model.SeriesSummary
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
    val items: List<LibraryGridItem> = emptyList(),
    val page: Int = 1,
    val endReached: Boolean = false,
    val error: KativaError? = null,
    val privacyMode: Boolean = false,
    val filter: SeriesFilter = SeriesFilter.Default,
) {
    val isEmpty: Boolean get() = !loading && error == null && items.isEmpty()

    /** True when the grid is empty *because* a filter is active. */
    val isEmptyDueToFilter: Boolean get() = isEmpty && !filter.isEmpty

    val sortLabelRes: SeriesSort get() = filter.sort
}

@HiltViewModel
class LibraryGridViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val coverUrlBuilder: CoverUrlBuilder,
    private val privacyModeState: com.kativa.reader.core.image.PrivacyModeState,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val libraryId: Int = savedStateHandle.get<String>("libraryId")?.toIntOrNull() ?: 0

    private val _state = MutableStateFlow(LibraryGridUiState())
    val state: StateFlow<LibraryGridUiState> = _state.asStateFlow()

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

    fun load(reset: Boolean = false) {
        val current = _state.value
        val nextPage = if (reset) 1 else current.page + 1
        _state.update {
            it.copy(
                loading = reset,
                loadingMore = !reset,
                error = null,
                page = nextPage,
            )
        }

        viewModelScope.launch {
            when (
                val outcome = libraryRepository.seriesInLibrary(
                    libraryId = libraryId,
                    page = nextPage,
                    filter = current.filter,
                )
            ) {
                is Outcome.Success -> {
                    val newItems = outcome.value.map { series ->
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
                            items = merged,
                            // A short page means we reached the end; no total is exposed reliably.
                            endReached = newItems.size < PAGE_SIZE,
                        )
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        error = outcome.failure.error,
                    )
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached) return
        load(reset = false)
    }

    // --- sorting / filtering --------------------------------------------

    fun setSort(sort: SeriesSort, ascending: Boolean) {
        applyFilter(_state.value.filter.withSort(sort, ascending))
    }

    fun applyFilter(filter: SeriesFilter) {
        _state.update { it.copy(filter = filter) }
        load(reset = true)
    }

    fun removeStatement(index: Int) {
        applyFilter(_state.value.filter.withoutStatement(index))
    }

    fun clearFilter() {
        val current = _state.value.filter
        applyFilter(SeriesFilter(sort = current.sort, ascending = current.ascending))
    }

    private companion object {
        const val PAGE_SIZE = 40
    }
}
