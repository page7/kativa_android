package com.kativa.reader.core.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Per-series reader settings (AGENTS.md §5, docs/PAGES.md P08).
 *
 * Resolution is *series → global default → app default*, implemented by
 * [ReaderSettingsLayers.resolve]; writes are always explicit — the reader never silently promotes a
 * per-series change into the global layer (that is the settings screen's job).
 *
 * Keys carry the server id, exactly like sessions and the privacy flag (AGENTS.md §4.1/§6): two
 * servers may well have a different series with the same numeric id.
 *
 * Storage lives in the shared `kativa_settings` DataStore ([settingsDataStore]) rather than a new
 * file — two `preferencesDataStore` delegates on the same file name throw at runtime.
 */
@Singleton
class ReaderSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Effective settings for one series, already collapsed down the fallback chain.
     *
     * A flow rather than a suspend read so the reader can react if the user edits the global
     * defaults from another screen while a chapter stays open.
     */
    fun settingsFor(serverId: String?, seriesId: Int): Flow<StoredReaderSettings> =
        context.settingsDataStore.data.map { prefs ->
            ReaderSettingsLayers.resolve(
                series = ReaderSettingsCodec.decode(prefs[seriesKey(serverId, seriesId)]),
                global = ReaderSettingsCodec.decode(prefs[keyGlobal]),
            )
        }

    /** The global-default layer on its own, for the settings screen (no series fallback). */
    val globalSettings: Flow<StoredReaderSettings> = context.settingsDataStore.data.map { prefs ->
        ReaderSettingsCodec.decode(prefs[keyGlobal]) ?: ReaderSettingsLayers.AppDefault
    }

    suspend fun setSeries(serverId: String?, seriesId: Int, value: StoredReaderSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[seriesKey(serverId, seriesId)] = ReaderSettingsCodec.encode(value)
        }
    }

    /**
     * Drops this series' overrides so it follows the global layer again.
     *
     * "Restore defaults" in the reader sheet: the user gets whatever the global defaults say,
     * which is what they configured on the settings screen, not the factory values.
     */
    suspend fun clearSeries(serverId: String?, seriesId: Int) {
        context.settingsDataStore.edit { prefs -> prefs.remove(seriesKey(serverId, seriesId)) }
    }

    suspend fun setGlobal(value: StoredReaderSettings) {
        context.settingsDataStore.edit { prefs -> prefs[keyGlobal] = ReaderSettingsCodec.encode(value) }
    }

    /** Removes every reader-settings entry belonging to a deleted server (AGENTS.md §4.1). */
    suspend fun clearServer(serverId: String) {
        val prefix = serverPrefix(serverId)
        context.settingsDataStore.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith(prefix) }
                .forEach { prefs.remove(it as Preferences.Key<*>) }
        }
    }

    private fun seriesKey(serverId: String?, seriesId: Int): Preferences.Key<String> =
        stringPreferencesKey(ReaderSettingsKeys.series(serverId, seriesId))

    private fun serverPrefix(serverId: String): String = ReaderSettingsKeys.serverPrefix(serverId)

    private val keyGlobal: Preferences.Key<String> = stringPreferencesKey(ReaderSettingsKeys.GLOBAL)
}

/**
 * Key naming, kept separate from the DataStore so isolation can be asserted on the JVM.
 *
 * Every key carries the server id (AGENTS.md §4.1): two servers can easily have different series
 * sharing a numeric id, and a shared key would leak one server's preferences into the other.
 */
internal object ReaderSettingsKeys {
    const val GLOBAL = "reader_settings_global"
    private const val SERIES_PREFIX = "reader_settings_"

    /** Used when no server is active; keeps a pre-login reader (never happens) crash-free. */
    private const val NO_SERVER = "none"

    fun series(serverId: String?, seriesId: Int): String = serverPrefix(serverId ?: NO_SERVER) + seriesId

    fun serverPrefix(serverId: String): String = "$SERIES_PREFIX$serverId${SEPARATOR}"

    private const val SEPARATOR = "_"
}
