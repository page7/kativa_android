package com.kativa.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.designsystem.ThemeMode
import com.kativa.reader.core.image.PrivacyModeState
import com.kativa.reader.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Activity-scoped state that must exist above the navigation graph: the theme and the privacy flag.
 *
 * The privacy flag is *not* computed here. [PrivacyModeState] owns that, together with the
 * active-server and DataStore observation, so the value rendered by [KativaTheme] is by
 * construction the same one the image layer sees. This ViewModel only exposes it for Compose.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    privacyModeState: PrivacyModeState,
) : ViewModel() {

    private val _themeMode = MutableStateFlow(ThemeMode.Dark)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /**
     * Mirrors [PrivacyModeState]. Screens that need to *toggle* privacy write through
     * [SettingsRepository]; this flow simply reflects the process-wide truth, including when the
     * change originated on another screen or from the settings switch.
     */
    val privacyMode: StateFlow<Boolean> = privacyModeState.enabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = privacyModeState.enabled.value,
    )

    init {
        viewModelScope.launch {
            settingsRepository.themeMode.collect { _themeMode.value = it }
        }
    }
}
