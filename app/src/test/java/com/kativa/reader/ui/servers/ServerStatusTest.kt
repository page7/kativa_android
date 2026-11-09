package com.kativa.reader.ui.servers

import com.kativa.reader.core.auth.Session
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Server picker status derivation.
 *
 * Regression guard for the shipped defect: `sessions.containsKey(id)` reported "SignedIn" for an
 * expired session, so tapping the server skipped the login form and the home screen could only
 * answer "session expired" — a loop with no exit.
 */
class ServerStatusTest {

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
    fun `missing session needs login`() {
        assertEquals(ServerStatus.NeedsLogin, serverStatusFor(null))
    }

    @Test
    fun `live session is signed in`() {
        assertEquals(
            ServerStatus.SignedIn,
            serverStatusFor(session(jwt(2000)), nowEpochSeconds = 1000),
        )
    }

    /** The bug: this must NOT be SignedIn, because that is what skipped the login screen. */
    @Test
    fun `expired session is not signed in`() {
        assertEquals(
            ServerStatus.Expired,
            serverStatusFor(session(jwt(1000)), nowEpochSeconds = 1001),
        )
    }

    @Test
    fun `only signed in cards may enter directly`() {
        val expired = ServerItemState(
            profile = com.kativa.reader.domain.model.ServerProfile(
                id = "a",
                name = "a",
                baseUrl = "https://a.example.com/api/",
            ),
            status = ServerStatus.Expired,
            hasSession = true,
        )
        assertEquals(false, expired.canEnterDirectly)
    }
}
