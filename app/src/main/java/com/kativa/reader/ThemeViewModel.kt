package com.kativa.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.designsystem.ThemeMode
import com.kativa.reader.core.image.PrivacyModeState
import com.kativa.reader.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Activity-scoped state that must exist above the navigation graph: the theme and the
 * privacy flag (which is also mirrored into [PrivacyModeState] for the image loader).
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val activeServerStore: ActiveServerStore,
    private val privacyModeState: PrivacyModeState,
) : ViewModel() {

    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _privacyMode = MutableStateFlow(false)
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.themeMode.collect { _themeMode.value = it }
        }
        viewModelScope.launch {
            activeServerStore.active.collect { server ->
                settingsRepository.isPrivacyMode(server?.id).collect { enabled ->
                    _privacyMode.value = enabled
                    privacyModeState.set(enabled)
                }
            }
        }
    }
}
