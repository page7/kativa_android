package com.kativa.reader.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.image.PrivacyModeState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeUiState(
    val username: String = "",
    val serverName: String = "",
    val isAdmin: Boolean = false,
    val isReadOnly: Boolean = false,
    val privacyMode: Boolean = false,
)

@HiltViewModel
class MeViewModel @Inject constructor(
    private val activeServerStore: ActiveServerStore,
    private val privacyModeState: PrivacyModeState,
) : ViewModel() {

    private val _state = MutableStateFlow(MeUiState())
    val state: StateFlow<MeUiState> = _state.asStateFlow()

    init {
        val session = activeServerStore.session.value
        val server = activeServerStore.active.value
        _state.update {
            it.copy(
                username = session?.username.orEmpty(),
                serverName = server?.displayName.orEmpty(),
                isAdmin = session?.isAdmin == true,
                isReadOnly = session?.isReadOnly == true,
            )
        }

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
}
