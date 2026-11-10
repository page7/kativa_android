package com.kavita.reader.core.auth

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision the server picker makes about a stored session.
 *
 * This is the regression guard for the shipped bug: the status was "a record exists", so an
 * expired token still read as "signed in" and tapping the server bypassed the login form.
 */
class SessionStatusTest {

    private fun jwt(exp: Long): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256"}""".toByteArray())
        val body = encoder.encodeToString("""{"exp":$exp}""".toByteArray())
        return "$header.$body.sig"
    }

    private fun session(token: String) = Session(
        serverId = "a",
        userId = 1,
        username = "reader",
        token = token,
        refreshToken = "r",
        imageKey = "k",
    )

    @Test
    fun `no session is missing`() {
        assertEquals(SessionStatus.Missing, (null as Session?).status())
    }

    @Test
    fun `live token is valid`() {
        assertEquals(SessionStatus.Valid, session(jwt(2000)).status(nowEpochSeconds = 1000))
    }

    @Test
    fun `dead token is expired rather than valid`() {
        assertEquals(SessionStatus.Expired, session(jwt(1000)).status(nowEpochSeconds = 1001))
    }

    /**
     * The picker must never offer a session that dies on the very next screen; a small skew keeps
     * a token with seconds left from being advertised as signed in.
     */
    @Test
    fun `soon to expire token is expired under skew`() {
        assertEquals(
            SessionStatus.Expired,
            session(jwt(1100)).status(nowEpochSeconds = 1000, skewSeconds = 300),
        )
    }

    @Test
    fun `opaque token is optimistically valid`() {
        // Never lock the user out because we could not parse the token.
        assertTrue(session("opaque").status() == SessionStatus.Valid)
    }
}
