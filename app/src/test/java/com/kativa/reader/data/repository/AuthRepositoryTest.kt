package com.kativa.reader.data.repository

import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.auth.RememberedAccount
import com.kativa.reader.core.auth.RememberedAccounts
import com.kativa.reader.core.auth.Session
import com.kativa.reader.core.auth.SessionExpiryBus
import com.kativa.reader.core.auth.SessionSink
import com.kativa.reader.core.di.DebugBuild
import com.kativa.reader.core.network.ApiFactory
import com.kativa.reader.core.network.CleartextGateInterceptor
import com.kativa.reader.core.network.PaginationInterceptor
import com.kativa.reader.core.network.SessionExpiryInterceptor
import com.kativa.reader.domain.model.ServerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Sign-in against a real HTTP stack.
 *
 * Covers the two behaviours that made "session expired" a dead end:
 *  - the account (username) is remembered so re-login only needs a password;
 *  - a 401 on a business call clears the stored token so the picker stops claiming "signed in".
 */
class AuthRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var store: ActiveServerStore
    private lateinit var sessions: FakeSessionSink
    private lateinit var accounts: FakeAccounts
    private lateinit var bus: SessionExpiryBus
    private lateinit var repository: AuthRepository

    private class FakeSessionSink : SessionSink {
        val saved = mutableMapOf<String, Session>()
        override suspend fun get(serverId: String): Session? = saved[serverId]
        override suspend fun save(session: Session) {
            saved[session.serverId] = session
        }

        override suspend fun clear(serverId: String) {
            saved.remove(serverId)
        }
    }

    private class FakeAccounts : RememberedAccounts {
        val saved = mutableMapOf<String, RememberedAccount>()
        override val accounts: Flow<Map<String, RememberedAccount>> = flowOf(saved)
        override suspend fun get(serverId: String): RememberedAccount? = saved[serverId]
        override suspend fun save(account: RememberedAccount) {
            saved[account.serverId] = account
        }

        override suspend fun clear(serverId: String) {
            saved.remove(serverId)
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = ActiveServerStore()
        sessions = FakeSessionSink()
        accounts = FakeAccounts()
        bus = SessionExpiryBus()

        val profile = ServerProfile(
            id = "s1",
            name = "home",
            baseUrl = server.url("/api/").toString(),
            allowCleartext = true,
        )
        store.activate(profile, null)

        val expiry = SessionExpiryInterceptor(store, bus)
        val apiFactory = ApiFactory(
            activeServerStore = store,
            paginationInterceptor = PaginationInterceptor(),
            cleartextGate = CleartextGateInterceptor { true },
            sessionExpiryInterceptor = expiry,
            isDebug = false,
        )

        repository = AuthRepository(
            apiFactory = apiFactory,
            sessionStore = sessions,
            activeServerStore = store,
            rememberedAccounts = accounts,
            sessionExpiryBus = bus,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun loginBody(username: String = "reader") = """
        {
          "id": 7,
          "username": "$username",
          "roles": ["Login", "Bookmark"],
          "token": "jwt-token",
          "refreshToken": "refresh-token",
          "apiKey": "opds-key",
          "authKeys": [{"id": 1, "key": "image-key", "name": "image-only"}],
          "kavitaVersion": "0.9.1.7"
        }
    """.trimIndent()

    /** The password must never be persisted; only the username is remembered. */
    @Test
    fun `password login stores the session and remembers the username`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(loginBody()))

        val outcome = repository.loginWithPassword("reader", "hunter2")
        assertTrue(outcome is com.kativa.reader.core.network.Outcome.Success)

        val session = sessions.saved["s1"]
        assertEquals("jwt-token", session?.token)
        assertEquals("image-key", session?.imageKey)

        val remembered = accounts.saved["s1"]
        assertEquals("reader", remembered?.username)
        // Nothing else is kept: no password field exists on RememberedAccount by design.
        assertTrue(remembered!!.hasUsername)
    }

    /**
     * API-key sign-in cannot know the username up front, so the one the server reports is stored.
     * That is what makes the next (expired) sign-in a password-only affair.
     */
    @Test
    fun `api key login remembers the server reported username`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(loginBody(username = "keyuser")))

        repository.loginWithApiKey("some-key")

        assertEquals("keyuser", accounts.saved["s1"]?.username)
    }

    @Test
    fun `invalidating a session clears the token but keeps the account`() = runTest {
        sessions.save(
            Session(
                serverId = "s1",
                userId = 1,
                username = "reader",
                token = "t",
                refreshToken = "r",
                imageKey = "k",
            ),
        )
        accounts.save(RememberedAccount(serverId = "s1", username = "reader"))
        store.updateSession(sessions.saved["s1"])

        repository.invalidateSession("s1", notify = false)

        assertNull(sessions.saved["s1"])
        assertNull(store.session.value)
        assertEquals("reader", accounts.saved["s1"]?.username)
    }

    @Test
    fun `invalidating notifies the expiry bus`() = runTest {
        accounts.save(RememberedAccount(serverId = "s1", username = "reader"))
        repository.invalidateSession("s1")

        assertEquals("s1", bus.events.first())
    }

    @Test
    fun `wrong password does not store anything`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("Your credentials are not correct"),
        )

        val outcome = repository.loginWithPassword("reader", "wrong")
        assertTrue(outcome is com.kativa.reader.core.network.Outcome.Failure)
        assertNull(sessions.saved["s1"])
        assertNull(accounts.saved["s1"])
        // A wrong password is a 401 too, but it must not fire the global expiry flow.
        assertNull(withTimeoutOrNull(200) { bus.events.first() })
    }

    private suspend fun <T> withTimeoutOrNull(ms: Long, block: suspend () -> T): T? =
        kotlinx.coroutines.withTimeoutOrNull(ms) { block() }
}
