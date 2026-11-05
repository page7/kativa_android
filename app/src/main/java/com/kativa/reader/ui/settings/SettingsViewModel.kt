package com.kativa.reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.auth.ServerRepository
import com.kativa.reader.core.designsystem.ThemeMode
import com.kativa.reader.core.settings.SettingsRepository
import com.kativa.reader.domain.model.ServerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val privacyMode: Boolean = false,
    val language: String? = null,
    val servers: List<ServerProfile> = emptyList(),
    val autoLoginServerId: String? = null,
    val activeServerId: String? = null,
    val versionName: String = "",
) {
    val autoLoginName: String?
        get() = servers.firstOrNull { it.id == autoLoginServerId }?.displayName
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val serverRepository: ServerRepository,
    private val activeServerStore: ActiveServerStore,
    private val authRepository: com.kativa.reader.data.repository.AuthRepository,
) : ViewModel() {

    private val activeId = MutableStateFlow(activeServerStore.activeId)

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.themeMode,
        settingsRepository.isPrivacyMode(activeServerStore.activeId),
        settingsRepository.language,
        serverRepository.servers,
        serverRepository.autoLoginServerId,
    ) { theme, privacy, language, servers, autoId ->
        SettingsUiState(
            themeMode = theme,
            privacyMode = privacy,
            language = language,
            servers = servers,
            autoLoginServerId = autoId,
            activeServerId = activeId.value,
            versionName = com.kativa.reader.BuildConfig.VERSION_NAME,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(versionName = com.kativa.reader.BuildConfig.VERSION_NAME),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setPrivacyMode(enabled: Boolean) {
        val id = activeServerStore.activeId ?: return
        viewModelScope.launch { settingsRepository.setPrivacyMode(id, enabled) }
    }

    fun setLanguage(tag: String?) {
        viewModelScope.launch { settingsRepository.setLanguage(tag) }
    }

    fun setAutoLogin(serverId: String?) {
        viewModelScope.launch { serverRepository.setAutoLogin(serverId) }
    }

    /** Clears this server's session only; other servers stay signed in (AGENTS.md §4.2). */
    fun signOutCurrent(onDone: () -> Unit) {
        viewModelScope.launch {
            val id = activeServerStore.activeId ?: return@launch
            authRepository.signOut(id)
            onDone()
        }
    }
}
