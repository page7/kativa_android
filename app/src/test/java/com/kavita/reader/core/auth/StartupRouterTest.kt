package com.kavita.reader.core.auth

import com.kavita.reader.domain.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Startup routing rule (AGENTS.md §1, docs/FLOWS.md F1):
 *  - no server → Welcome
 *  - servers but no auto-login → ServerPicker   ← the default
 *  - auto-login with a live session → Home
 *  - auto-login without a session → ServerPicker (never a dead end)
 */
class StartupRouterTest {

    private val router = StartupRouter(ActiveServerStore())

    private fun server(id: String) = ServerProfile(
        id = id,
        name = id,
        baseUrl = "https://$id.example.com/api/",
    )

    @Test
    fun `no servers goes to welcome`() {
        assertEquals(
            StartupRouter.Destination.Welcome,
            router.decide(emptyList(), null) { false },
        )
    }

    @Test
    fun `servers without auto login go to the picker`() {
        assertEquals(
            StartupRouter.Destination.ServerPicker,
            router.decide(listOf(server("a"), server("b")), null) { true },
        )
    }

    @Test
    fun `auto login server with a session goes home`() {
        assertEquals(
            StartupRouter.Destination.Home,
            router.decide(listOf(server("a")), "a") { true },
        )
    }

    @Test
    fun `auto login server without a session falls back to the picker`() {
        assertEquals(
            StartupRouter.Destination.ServerPicker,
            router.decide(listOf(server("a")), "a") { false },
        )
    }

    @Test
    fun `auto login id pointing at a deleted server falls back to the picker`() {
        assertEquals(
            StartupRouter.Destination.ServerPicker,
            router.decide(listOf(server("a")), "ghost") { true },
        )
    }

    @Test
    fun `display name falls back to host when no name is set`() {
        val unnamed = ServerProfile(
            id = "a",
            name = "",
            baseUrl = "https://kavita.example.com/api/",
        )
        assertEquals("kavita.example.com", unnamed.displayName)
    }

    @Test
    fun `display name prefers the explicit name`() {
        val named = ServerProfile(
            id = "a",
            name = "Home",
            baseUrl = "https://kavita.example.com/api/",
        )
        assertEquals("Home", named.displayName)
    }

    @Test
    fun `read only session is detected from roles`() {
        val session = Session(
            serverId = "a",
            userId = 1,
            username = "demo",
            roles = listOf("Login", "Read Only"),
            token = "t",
            refreshToken = "r",
            imageKey = "k",
        )
        assertEquals(true, session.isReadOnly)
        assertEquals(false, session.isAdmin)
    }

    @Test
    fun `admin session also implies bookmark rights`() {
        val session = Session(
            serverId = "a",
            userId = 1,
            username = "root",
            roles = listOf("Admin"),
            token = "t",
            refreshToken = "r",
            imageKey = "k",
        )
        assertEquals(true, session.isAdmin)
        assertEquals(true, session.canBookmark)
    }

    @Test
    fun `active server store isolates sessions`() {
        val store = ActiveServerStore()
        assertNull(store.active.value)
        store.activate(server("a"), null)
        assertNotNull(store.active.value)
        assertEquals("a", store.activeId)
        store.clear()
        assertNull(store.activeId)
        assertNull(store.session.value)
    }
}
