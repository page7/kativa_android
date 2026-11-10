package com.kativa.reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.auth.ServerRepository
import com.kativa.reader.core.designsystem.ThemeMode
import com.kativa.reader.core.image.PrivacyModeState
import com.kativa.reader.core.settings.ReaderSettingsStore
import com.kativa.reader.core.settings.SettingsRepository
import com.kativa.reader.domain.model.ServerProfile
import com.kativa.reader.ui.reader.ReaderSettings
import com.kativa.reader.ui.reader.ReaderSettingsPersistence
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
    /** Global reader defaults — the layer a series falls back to when it has no override. */
    val readerSettings: ReaderSettings = ReaderSettings.Default,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val serverRepository: ServerRepository,
    private val activeServerStore: ActiveServerStore,
    private val privacyModeState: PrivacyModeState,
    private val authRepository: com.kativa.reader.data.repository.AuthRepository,
    private val readerSettingsStore: ReaderSettingsStore,
) : ViewModel() {

    private val activeId = MutableStateFlow(activeServerStore.activeId)

    val state: StateFlow<SettingsUiState> = combine(
        combine(
            settingsRepository.themeMode,
            // Read the shared flag, not `isPrivacyMode(activeId)`: the latter is bound to whichever
            // server was active when this ViewModel was built, so switching servers left the switch
            // showing the previous server's value.
            privacyModeState.enabled,
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
        },
        readerSettingsStore.globalSettings,
    ) { base, reader ->
        base.copy(readerSettings = ReaderSettingsPersistence.toRenderModel(reader, base.readerSettings))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(versionName = com.kativa.reader.BuildConfig.VERSION_NAME),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setPrivacyMode(enabled: Boolean) {
        privacyModeState.toggleForActiveServer(enabled)
    }

    fun setLanguage(tag: String?) {
        viewModelScope.launch { settingsRepository.setLanguage(tag) }
    }

    fun setAutoLogin(serverId: String?) {
        viewModelScope.launch { serverRepository.setAutoLogin(serverId) }
    }

    /**
     * Edits the **global** reader defaults. Nothing here touches a per-series override: the reader
     * sheet is the only writer of those, which keeps "restore this series" meaningful.
     */
    fun updateReaderDefaults(transform: (ReaderSettings) -> ReaderSettings) {
        val next = transform(state.value.readerSettings)
        viewModelScope.launch {
            readerSettingsStore.setGlobal(ReaderSettingsPersistence.toStored(next))
        }
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
