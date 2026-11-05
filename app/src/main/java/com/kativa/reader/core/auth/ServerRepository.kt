package com.kativa.reader.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kativa.reader.domain.model.ServerProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.serverDataStore: DataStore<Preferences> by preferencesDataStore(name = "kativa_servers")

/**
 * Persists the server list and the startup preference.
 *
 * Sessions (tokens) live in [SessionStore], keyed by server id — never mixed together.
 * Server addresses are not secrets, so they are stored as plain JSON.
 */
@Singleton
class ServerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val keyServers = stringPreferencesKey("servers")
    private val keyAutoLoginId = stringPreferencesKey("auto_login_server_id")

    val servers: Flow<List<ServerProfile>> = context.serverDataStore.data.map { prefs ->
        prefs[keyServers]?.let { decode(it) } ?: emptyList()
    }

    val autoLoginServerId: Flow<String?> = context.serverDataStore.data.map { it[keyAutoLoginId] }

    suspend fun getServers(): List<ServerProfile> = servers.first()

    suspend fun getServer(id: String): ServerProfile? = getServers().firstOrNull { it.id == id }

    suspend fun getAutoLoginServer(): ServerProfile? {
        val id = autoLoginServerId.first() ?: return null
        return getServer(id)
    }

    suspend fun upsert(profile: ServerProfile) {
        context.serverDataStore.edit { prefs ->
            val current = prefs[keyServers]?.let { decode(it) }.orEmpty()
            val next = current.filterNot { it.id == profile.id } + profile
            prefs[keyServers] = json.encodeToStringServerList(next.map { it.toRecord() })
        }
    }

    suspend fun touch(profile: ServerProfile, at: Long = System.currentTimeMillis()) {
        upsert(profile.copy(lastUsedAt = at))
    }

    suspend fun delete(id: String) {
        context.serverDataStore.edit { prefs ->
            val current = prefs[keyServers]?.let { decode(it) }.orEmpty()
            prefs[keyServers] = json.encodeToStringServerList(current.filterNot { it.id == id }.map { it.toRecord() })
            if (prefs[keyAutoLoginId] == id) prefs.remove(keyAutoLoginId)
        }
    }

    suspend fun setAutoLogin(serverId: String?) {
        context.serverDataStore.edit { prefs ->
            if (serverId == null) prefs.remove(keyAutoLoginId) else prefs[keyAutoLoginId] = serverId
        }
    }

    private fun decode(raw: String): List<ServerProfile> =
        runCatching {
            json.decodeFromString(ServerRecordList.serializer(), raw).items.map { it.toDomain() }
        }.getOrDefault(emptyList())

    private fun Json.encodeToStringServerList(list: List<ServerRecord>): String =
        encodeToString(ServerRecordList.serializer(), ServerRecordList(list))

    @Serializable
    private data class ServerRecordList(val items: List<ServerRecord> = emptyList())

    @Serializable
    private data class ServerRecord(
        val id: String,
        val name: String,
        val baseUrl: String,
        val allowCleartext: Boolean = false,
        val tlsFingerprint: String? = null,
        val lastUsedAt: Long? = null,
    ) {
        fun toDomain() = ServerProfile(
            id = id,
            name = name,
            baseUrl = baseUrl,
            allowCleartext = allowCleartext,
            tlsFingerprint = tlsFingerprint,
            lastUsedAt = lastUsedAt,
        )
    }

    private fun ServerProfile.toRecord() = ServerRecord(
        id = id,
        name = name,
        baseUrl = baseUrl,
        allowCleartext = allowCleartext,
        tlsFingerprint = tlsFingerprint,
        lastUsedAt = lastUsedAt,
    )
}
