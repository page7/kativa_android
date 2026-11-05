package com.kativa.reader.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kativa.reader.core.designsystem.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "kativa_settings")

/**
 * App-level preferences: theme, language, and the persisted privacy-mode switch.
 *
 * Privacy mode is per server, exactly like sessions and caches (AGENTS.md §6).
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyLanguage = stringPreferencesKey("language")
    private val keyPrivacyPrefix = "privacy_"

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        // Dark by default: this is a reading app and the brand palette is designed dark-first
        // (AGENTS.md §6). "Follow system" remains an explicit user choice.
        prefs[keyTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.Dark
    }

    val language: Flow<String?> = context.settingsDataStore.data.map { it[keyLanguage] }

    fun isPrivacyMode(serverId: String?): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            serverId != null && (prefs[booleanPreferencesKey(keyPrivacyPrefix + serverId)] ?: false)
        }

    suspend fun setPrivacyMode(serverId: String, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[booleanPreferencesKey(keyPrivacyPrefix + serverId)] = enabled
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs -> prefs[keyTheme] = mode.name }
    }

    suspend fun setLanguage(tag: String?) {
        context.settingsDataStore.edit { prefs ->
            if (tag == null) prefs.remove(keyLanguage) else prefs[keyLanguage] = tag
        }
    }

    suspend fun clearPrivacyFor(serverId: String) {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(booleanPreferencesKey(keyPrivacyPrefix + serverId))
        }
    }
}
