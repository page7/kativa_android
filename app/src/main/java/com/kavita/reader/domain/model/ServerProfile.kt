package com.kavita.reader.domain.model

/**
 * One saved Kavita server.
 *
 * [id] is a local UUID and is the isolation key for sessions, Room projections,
 * image caches and the progress queue. Never reuse a token across ids.
 */
data class ServerProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val allowCleartext: Boolean = false,
    /** SHA-256 fingerprint accepted via TOFU, or null when the certificate validated normally. */
    val tlsFingerprint: String? = null,
    val lastUsedAt: Long? = null,
) {
    /** Display label: user name, else host, else raw address. */
    val displayName: String
        get() = name.ifBlank {
            runCatching { java.net.URI(baseUrl).host }.getOrNull() ?: baseUrl
        }
}
