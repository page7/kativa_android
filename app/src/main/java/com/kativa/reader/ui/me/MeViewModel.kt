package com.kativa.reader.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.settings.SettingsRepository
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
    private val settingsRepository: SettingsRepository,
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
}
