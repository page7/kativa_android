package com.kativa.reader.core.auth

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expiry detection from the JWT `exp` claim.
 *
 * Why this matters: a stored-but-dead token used to make the server picker claim "signed in",
 * which put the user in a loop that only clearing app data could escape.
 */
class JwtExpiryTest {

    private fun jwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "$header.$body.signature"
    }

    @Test
    fun `future exp is not expired`() {
        val token = jwt("""{"sub":"1","exp":2000}""")
        assertFalse(JwtExpiry.isExpired(token, nowEpochSeconds = 1000))
        assertEquals(2000L, JwtExpiry.expiryEpochSeconds(token))
    }

    @Test
    fun `past exp is expired`() {
        val token = jwt("""{"sub":"1","exp":1000}""")
        assertTrue(JwtExpiry.isExpired(token, nowEpochSeconds = 1001))
    }

    @Test
    fun `exp exactly at now counts as expired`() {
        val token = jwt("""{"exp":1000}""")
        assertTrue(JwtExpiry.isExpired(token, nowEpochSeconds = 1000))
    }

    @Test
    fun `skew treats a soon to expire token as expired`() {
        val token = jwt("""{"exp":1100}""")
        assertFalse(JwtExpiry.isExpired(token, nowEpochSeconds = 1000, skewSeconds = 0))
        assertTrue(JwtExpiry.isExpired(token, nowEpochSeconds = 1000, skewSeconds = 300))
    }

    /**
     * An opaque token (or a future server format change) must not lock the user out: without a
     * readable `exp` the conservative answer is "still usable".
     */
    @Test
    fun `opaque token is treated as not expired`() {
        assertFalse(JwtExpiry.isExpired("not-a-jwt", nowEpochSeconds = 10))
        assertNull(JwtExpiry.expiryEpochSeconds("not-a-jwt"))
    }

    @Test
    fun `jwt without exp claim is not expired`() {
        val token = jwt("""{"sub":"1"}""")
        assertFalse(JwtExpiry.isExpired(token, nowEpochSeconds = 10))
    }

    @Test
    fun `padded and unpadded base64 both decode`() {
        val payload = """{"exp":4242}"""
        val encoder = Base64.getUrlEncoder()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray()) // padded
        assertEquals(4242L, JwtExpiry.expiryEpochSeconds("$header.$body.sig"))
    }
}
