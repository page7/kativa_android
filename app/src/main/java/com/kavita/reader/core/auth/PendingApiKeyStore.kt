package com.kavita.reader.core.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot, in-memory hand-off for an API key extracted from a pasted link.
 *
 * Users commonly paste Kavita's OPDS feed URL (`http://host:port/api/opds/<key>`) into the
 * address field. We already stripped that path and recovered the key; without this store the
 * key was silently discarded and the user had to find and retype it (AGENTS.md §4.1).
 *
 * Deliberately **not** persisted: it is a secret, and it only needs to survive the trip from
 * "save server" to "login screen". Reading it clears it.
 */
@Singleton
class PendingApiKeyStore @Inject constructor() {

    private val keys = mutableMapOf<String, String>()

    fun put(serverId: String, apiKey: String) {
        if (apiKey.isNotBlank()) keys[serverId] = apiKey
    }

    /** Returns the pending key for [serverId] and forgets it. */
    fun consume(serverId: String): String? = keys.remove(serverId)

    fun clear() = keys.clear()
}
