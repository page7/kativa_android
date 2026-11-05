package com.kativa.reader.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.image.CoverUrlBuilder
import com.kativa.reader.core.network.KativaError
import com.kativa.reader.core.network.Outcome
import com.kativa.reader.core.settings.SettingsRepository
import com.kativa.reader.data.repository.LibraryRepository
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
    val sortField: Int = 1,
    val ascending: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && error == null && items.isEmpty()
}

@HiltViewModel
class LibraryGridViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val coverUrlBuilder: CoverUrlBuilder,
    private val activeServerStore: ActiveServerStore,
    private val settingsRepository: SettingsRepository,
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
            settingsRepository.isPrivacyMode(activeServerStore.activeId).collect { enabled ->
                _state.update { it.copy(privacyMode = enabled) }
            }
        }
    }

    fun setPrivacyMode(enabled: Boolean) {
        val id = activeServerStore.activeId ?: return
        _state.update { it.copy(privacyMode = enabled) }
        viewModelScope.launch { settingsRepository.setPrivacyMode(id, enabled) }
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
            when (val outcome = libraryRepository.seriesInLibrary(
                libraryId = libraryId,
                page = nextPage,
                sortField = current.sortField,
                ascending = current.ascending,
            )) {
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

    fun setSort(field: Int, ascending: Boolean) {
        _state.update { it.copy(sortField = field, ascending = ascending) }
        load(reset = true)
    }

    private companion object {
        const val PAGE_SIZE = 40
    }
}
