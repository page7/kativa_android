package com.kativa.reader.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "kativa_sessions")

/** Everything needed to talk to one server on the user's behalf. */
@Serializable
data class Session(
    val serverId: String,
    val userId: Int,
    val username: String,
    val roles: List<String> = emptyList(),
    val token: String,
    val refreshToken: String,
    /** Auth key used in `?apiKey=` for covers and reader images. */
    val imageKey: String,
    val kavitaVersion: String = "",
) {
    val isReadOnly: Boolean get() = roles.any { it.equals("Read Only", ignoreCase = true) }
    val isAdmin: Boolean get() = roles.any { it.equals("Admin", ignoreCase = true) }
    val canBookmark: Boolean get() = isAdmin || roles.any { it.equals("Bookmark", ignoreCase = true) }
}

/**
 * Token storage, keyed by server id.
 *
 * NOTE: for M0 this is DataStore-backed. The Keystore-backed encryption wrapper is tracked
 * as a follow-up before any public release (AGENTS.md §4.2), and the API surface will not change.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SessionSink {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** All sessions, keyed by server id. */
    val sessions: Flow<Map<String, Session>> = context.sessionDataStore.data.map { prefs ->
        prefs.asMap().entries
            .filter { it.key.name.startsWith(PREFIX) }
            .mapNotNull { (key, value) ->
                val raw = value as? String ?: return@mapNotNull null
                runCatching { json.decodeFromString(Session.serializer(), raw) }.getOrNull()
            }
            .associateBy { it.serverId }
    }

    fun sessionFlow(serverId: String): Flow<Session?> = sessions.map { it[serverId] }

    override suspend fun get(serverId: String): Session? = sessions.first()[serverId]

    override suspend fun save(session: Session) {
        context.sessionDataStore.edit { prefs ->
            val key = stringPreferencesKey(PREFIX + session.serverId)
            prefs[key] = json.encodeToString(Session.serializer(), session)
        }
    }

    override suspend fun clear(serverId: String) {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(PREFIX + serverId))
        }
    }

    suspend fun clearAll() {
        context.sessionDataStore.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith(PREFIX) }
                .forEach { prefs.remove(it) }
        }
    }

    private companion object {
        const val PREFIX = "session_"
    }
}
