package com.kavita.reader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavita.reader.core.image.CoverUrlBuilder
import com.kavita.reader.core.image.PrivacyModeState
import com.kavita.reader.core.network.KavitaError
import com.kavita.reader.core.network.Outcome
import com.kavita.reader.data.remote.api.LibraryDto
import com.kavita.reader.data.repository.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryListItem(
    val id: Int,
    val name: String,
    val type: Int,
    val coverUrl: String?,
    val lastScanned: String? = null,
)

data class LibraryUiState(
    val loading: Boolean = true,
    val items: List<LibraryListItem> = emptyList(),
    val error: KavitaError? = null,
    val privacyMode: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && error == null && items.isEmpty()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val seriesRepository: SeriesRepository,
    private val coverUrlBuilder: CoverUrlBuilder,
    private val privacyModeState: PrivacyModeState,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

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

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val outcome = seriesRepository.libraries()) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        loading = false,
                        items = outcome.value.map { dto -> dto.toItem() },
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, error = outcome.failure.error)
                }
            }
        }
    }

    fun setPrivacyMode(enabled: Boolean) {
        privacyModeState.toggleForActiveServer(enabled)
        _state.update { it.copy(privacyMode = enabled) }
    }

    private fun LibraryDto.toItem() = LibraryListItem(
        id = id,
        name = name,
        type = type,
        coverUrl = coverUrlBuilder.library(id),
        lastScanned = lastScanned,
    )
}
