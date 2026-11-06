package com.kativa.reader.ui.shelf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.image.PrivacyModeState
import com.kativa.reader.core.network.KativaError
import com.kativa.reader.core.network.Outcome
import com.kativa.reader.core.settings.SettingsRepository
import com.kativa.reader.data.repository.ShelfCollection
import com.kativa.reader.data.repository.ShelfRepository
import com.kativa.reader.data.repository.SeriesHit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which shelf a screen is showing. */
enum class ShelfKind { Collections, ReadingLists, WantToRead }

data class ShelfUiState(
    val loading: Boolean = true,
    val shelves: List<ShelfCollection> = emptyList(),
    val series: List<SeriesHit> = emptyList(),
    val error: KativaError? = null,
    val privacyMode: Boolean = false,
    val title: String = "",
) {
    val isEmpty: Boolean
        get() = !loading && error == null && shelves.isEmpty() && series.isEmpty()
}

/**
 * One ViewModel covers all three shelf screens because they only differ in which endpoint they
 * call and whether they render collections or series. [detailId] switches a screen from "list of
 * shelves" to "series inside this shelf".
 */
@HiltViewModel
class ShelfViewModel @Inject constructor(
    private val shelfRepository: ShelfRepository,
    private val privacyModeState: PrivacyModeState,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val kind: ShelfKind = when (savedStateHandle.get<String>("kind")) {
        "lists" -> ShelfKind.ReadingLists
        "want-to-read" -> ShelfKind.WantToRead
        else -> ShelfKind.Collections
    }

    /** Set when opening a collection's contents. */
    private val detailId: Int = savedStateHandle.get<String>("shelfId")?.toIntOrNull() ?: 0

    private val _state = MutableStateFlow(ShelfUiState())
    val state: StateFlow<ShelfUiState> = _state.asStateFlow()

    val isDetail: Boolean get() = detailId > 0

    val shelfKind: ShelfKind get() = kind

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

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            if (detailId > 0) {
                // Collection detail: series inside it.
                when (val outcome = shelfRepository.seriesInCollection(detailId)) {
                    is Outcome.Success -> _state.update {
                        it.copy(loading = false, series = outcome.value)
                    }

                    is Outcome.Failure -> _state.update {
                        it.copy(loading = false, error = outcome.failure.error)
                    }
                }
                return@launch
            }

            when (kind) {
                ShelfKind.WantToRead -> when (val outcome = shelfRepository.wantToRead()) {
                    is Outcome.Success -> _state.update {
                        it.copy(loading = false, series = outcome.value)
                    }

                    is Outcome.Failure -> _state.update {
                        it.copy(loading = false, error = outcome.failure.error)
                    }
                }

                ShelfKind.ReadingLists -> when (val outcome = shelfRepository.readingLists()) {
                    is Outcome.Success -> _state.update {
                        it.copy(loading = false, shelves = outcome.value)
                    }

                    is Outcome.Failure -> _state.update {
                        it.copy(loading = false, error = outcome.failure.error)
                    }
                }

                ShelfKind.Collections -> when (val outcome = shelfRepository.collections()) {
                    is Outcome.Success -> _state.update {
                        it.copy(loading = false, shelves = outcome.value)
                    }

                    is Outcome.Failure -> _state.update {
                        it.copy(loading = false, error = outcome.failure.error)
                    }
                }
            }
        }
    }

    /** Removes a series from Want To Read, with a reload on success. */
    fun removeFromWantToRead(seriesId: Int) {
        viewModelScope.launch {
            if (shelfRepository.removeFromWantToRead(seriesId) is Outcome.Success) load()
        }
    }
}
