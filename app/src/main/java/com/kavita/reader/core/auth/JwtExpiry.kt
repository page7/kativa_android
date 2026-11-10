package com.kavita.reader.core.auth

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Reads the `exp` claim out of Kavita's JWT.
 *
 * Why this exists: Kavita's access token is short-lived (and the server may be configured for an
 * even shorter lifetime), but nothing on the client ever noticed. Two days later the token was
 * dead while the app still believed it was signed in — the server picker showed "signed in",
 * tapping the server skipped the login screen and the home screen then failed with
 * "session expired". The only escape was to clear the app's data.
 *
 * Deliberately dependency-free (plain `java.util.Base64`, API 26+) so it is pure JVM code that
 * can be unit tested without Robolectric.
 */
object JwtExpiry {

    /** Returns the `exp` claim in epoch seconds, or null when the token is not a readable JWT. */
    fun expiryEpochSeconds(token: String): Long? {
        val payload = decodePayload(token) ?: return null
        return runCatching {
            val obj = Json.parseToJsonElement(payload) as JsonObject
            obj["exp"]?.jsonPrimitive?.longOrNull
        }.getOrNull()
    }

    /**
     * True only when the token demonstrably expires at/before [nowEpochSeconds] + [skewSeconds].
     *
     * An unparsable token (opaque string, future server change) is treated as NOT expired: the
     * conservative default is to keep working and let a real 401 trigger the expired flow, rather
     * than locking the user out of a perfectly valid session.
     */
    fun isExpired(
        token: String,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
        skewSeconds: Long = 0,
    ): Boolean {
        val exp = expiryEpochSeconds(token) ?: return false
        return exp <= nowEpochSeconds + skewSeconds
    }

    private fun decodePayload(token: String): String? {
        val parts = token.split('.')
        if (parts.size != 3) return null
        return runCatching {
            // JWT uses unpadded base64url; the JDK decoder accepts a missing tail padding.
            String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        }.getOrNull()
    }
}
