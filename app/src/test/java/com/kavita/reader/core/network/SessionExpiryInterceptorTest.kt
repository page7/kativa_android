package com.kavita.reader.core.network

import com.kavita.reader.core.auth.ActiveServerStore
import com.kavita.reader.core.auth.Session
import com.kavita.reader.core.auth.SessionExpiryBus
import com.kavita.reader.domain.model.ServerProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * A 401 on an authenticated request must raise a "session expired" event; a 401 on the sign-in
 * endpoint must not (a wrong password is also a 401 and belongs on the form).
 */
class SessionExpiryInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var store: ActiveServerStore
    private lateinit var bus: SessionExpiryBus
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = ActiveServerStore()
        bus = SessionExpiryBus()
        store.activate(
            ServerProfile(id = "a", name = "a", baseUrl = server.url("/api/").toString()),
            Session(
                serverId = "a",
                userId = 1,
                username = "u",
                token = "t",
                refreshToken = "r",
                imageKey = "k",
            ),
        )
        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .addInterceptor(SessionExpiryInterceptor(store, bus))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun get(path: String): Int = client.newCall(
        Request.Builder().url(server.url("/api/$path")).build(),
    ).execute().use { it.code }

    private suspend fun nextEventOrNull(): String? =
        withTimeoutOrNull(300) { bus.events.first() }

    @Test
    fun `401 on a business endpoint emits an expiry event`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        assertEquals(401, get("Series/v2"))

        assertEquals("a", nextEventOrNull())
    }

    @Test
    fun `401 on login does not emit`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("Your credentials are not correct"),
        )
        assertEquals(401, get("Account/login"))

        assertNull(nextEventOrNull())
    }

    @Test
    fun `200 does not emit`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        assertEquals(200, get("Series/v2"))

        assertNull(nextEventOrNull())
    }
}
