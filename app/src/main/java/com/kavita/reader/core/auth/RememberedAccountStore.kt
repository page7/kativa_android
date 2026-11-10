package com.kavita.reader.core.auth

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

private val Context.accountDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "kavita_accounts")

/**
 * The non-secret half of a sign-in, remembered per server.
 *
 * Product requirement: when a session expires, re-login must not ask for the account again —
 * the username is already known, only the password is needed. The password (or API key) is
 * **never** persisted: AGENTS.md §4.2 forbids storing credentials at rest, and P0 deliberately
 * does not add Keystore-backed secret storage.
 *
 * Kept separate from [SessionStore] on purpose: sign-out / expiry clears the session but must
 * keep the account so the next sign-in is one field shorter.
 */
@Serializable
data class RememberedAccount(
    val serverId: String,
    val username: String,
) {
    val hasUsername: Boolean get() = username.isNotBlank()
}

@Singleton
class RememberedAccountStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : RememberedAccounts {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Every remembered account keyed by server id. */
    override val accounts: Flow<Map<String, RememberedAccount>> = context.accountDataStore.data.map { prefs ->
        prefs.asMap().entries
            .filter { it.key.name.startsWith(PREFIX) }
            .mapNotNull { (key, value) ->
                val raw = value as? String ?: return@mapNotNull null
                runCatching { json.decodeFromString(RememberedAccount.serializer(), raw) }.getOrNull()
            }
            .associateBy { it.serverId }
    }

    fun accountFlow(serverId: String): Flow<RememberedAccount?> = accounts.map { it[serverId] }

    override suspend fun get(serverId: String): RememberedAccount? = accounts.first()[serverId]

    /** No-op for a blank username: an empty hint would only make the form look broken. */
    override suspend fun save(account: RememberedAccount) {
        if (!account.hasUsername) return
        context.accountDataStore.edit { prefs ->
            prefs[stringPreferencesKey(PREFIX + account.serverId)] =
                json.encodeToString(RememberedAccount.serializer(), account)
        }
    }

    override suspend fun clear(serverId: String) {
        context.accountDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(PREFIX + serverId))
        }
    }

    private companion object {
        const val PREFIX = "account_"
    }
}
