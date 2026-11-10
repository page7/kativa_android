package com.kavita.reader.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kavita.reader.core.designsystem.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single DataStore for app preferences. `internal` because [ReaderSettingsStore] shares the same
 * file — declaring a second delegate for the same name would throw at runtime
 * ("multiple DataStores active for the same file").
 */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "kavita_settings")

/**
 * App-level preferences: theme, language, and the persisted privacy-mode switch.
 *
 * Privacy mode is per server, exactly like sessions and caches (AGENTS.md §6).
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : com.kavita.reader.core.image.PrivacySettings {
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyLanguage = stringPreferencesKey("language")
    private val keyPrivacyPrefix = "privacy_"

    val themeMode: Flow<ThemeMode> = themeFlow()

    /**
     * Theme readable synchronously, for the frame painted before Compose draws.
     *
     * `Activity.onCreate` cannot suspend, and awaiting DataStore's first emission there measured
     * **+5.8s to first frame** on a cold start (it blocks the very thread DataStore needs). So the
     * value is mirrored into a plain `SharedPreferences` whenever it is written or observed, and
     * the window background reads *that* (a single tiny XML load) instead. [ThemeMode.Dark] is the
     * pre-mirror default, which is also the app's shipped default.
     */
    fun cachedThemeMode(): ThemeMode = mirror.getString(KEY_THEME_MIRROR, null)
        ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
        ?: ThemeMode.Dark

    private val mirror: SharedPreferences =
        context.getSharedPreferences(MIRROR_PREFS, Context.MODE_PRIVATE)

    private fun themeFlow(): Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        // Dark by default: this is a reading app and the brand palette is designed dark-first
        // (AGENTS.md §6). "Follow system" remains an explicit user choice.
        prefs[keyTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.Dark
    }

    /**
     * Keeps the synchronous mirror in step with DataStore.
     *
     * Called once at process start; it does not block, so the first frame is never delayed. The
     * first emission also backfills the mirror for users upgrading from a build that predates it
     * (their first post-upgrade splash may still use the dark default; every later one is right).
     */
    suspend fun observeThemeForWindow() {
        themeMode.collect { mode ->
            mirror.edit().putString(KEY_THEME_MIRROR, mode.name).apply()
        }
    }

    val language: Flow<String?> = context.settingsDataStore.data.map { it[keyLanguage] }

    /**
     * Every stored privacy flag, keyed by server id.
     *
     * A single map rather than one flow per server: the active-server observer needs to react to
     * *any* server's flag changing while the active id itself is stable, and per-server flows
     * cannot express that without re-subscribing (which is exactly the bug this replaced �?see
     * [com.kavita.reader.core.image.PrivacyModeState]).
     */
    override val privacyModeByServer: Flow<Map<String, Boolean>> = context.settingsDataStore.data.map { prefs ->
        prefs.asMap()
            .filterKeys { key -> key.name.startsWith(keyPrivacyPrefix) }
            .mapNotNull { (key, value) ->
                val enabled = value as? Boolean ?: return@mapNotNull null
                key.name.removePrefix(keyPrivacyPrefix) to enabled
            }
            .toMap()
    }

    fun isPrivacyMode(serverId: String?): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            serverId != null && (prefs[booleanPreferencesKey(keyPrivacyPrefix + serverId)] ?: false)
        }

    override suspend fun setPrivacyMode(serverId: String, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[booleanPreferencesKey(keyPrivacyPrefix + serverId)] = enabled
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs -> prefs[keyTheme] = mode.name }
        // Write the mirror eagerly too: a theme change followed immediately by a cold start (user
        // picks Light, then kills the app) must not show the old backdrop on the way in.
        mirror.edit().putString(KEY_THEME_MIRROR, mode.name).apply()
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

    private companion object {
        /** Plain-XML mirror of [themeMode], readable without suspending (see [cachedThemeMode]). */
        const val MIRROR_PREFS = "kavita_theme_mirror"
        const val KEY_THEME_MIRROR = "theme_mode"
    }
}
