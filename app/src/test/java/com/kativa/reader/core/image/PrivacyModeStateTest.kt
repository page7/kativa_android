package com.kativa.reader.core.image

import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.domain.model.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The regression this locks down: privacy mode was resolved by nesting two `collect` calls.
 *
 *     activeServerStore.active.collect { server ->
 *         settingsRepository.isPrivacyMode(server.id).collect { ... }   // never completes
 *     }
 *
 * The inner flow never returns, so the outer collector received the startup `null` server and then
 * never saw the user actually select one. The flag stayed `false` forever, which is why every
 * screen above home rendered covers. A plain `combine` (what the production code now uses) is what
 * makes the second emission observable, so this test asserts exactly that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyModeStateTest {

    private class FakeSettings(initial: Map<String, Boolean> = emptyMap()) : PrivacySettings {
        val stored = MutableStateFlow(initial)
        override val privacyModeByServer: Flow<Map<String, Boolean>> = stored
        override suspend fun setPrivacyMode(serverId: String, enabled: Boolean) {
            stored.value = stored.value + (serverId to enabled)
        }
    }

    private fun server(id: String) = ServerProfile(id = id, name = id, baseUrl = "https://$id.example.com/api/")

    private fun state(
        store: ActiveServerStore,
        settings: PrivacySettings,
    ): PrivacyModeState = PrivacyModeState(store, settings, CoroutineScope(dispatcher))

    private lateinit var dispatcher: kotlinx.coroutines.test.TestDispatcher

    @Test
    fun `flag follows the server selected after startup`() = runTest {
        dispatcher = StandardTestDispatcher(testScheduler)
        val store = ActiveServerStore()
        val state = state(store, FakeSettings(mapOf("s1" to true)))

        // Startup: no server yet, so privacy must be off rather than bleeding a stale value.
        advanceUntilIdle()
        assertFalse(state.enabled.value)

        // The user picks a server whose flag is already on. This is the emission that used to be
        // swallowed by the nested collector.
        store.activate(server("s1"), null)
        advanceUntilIdle()
        assertTrue("privacy must follow the active server", state.enabled.value)
    }

    @Test
    fun `switching servers reads the new server's own flag`() = runTest {
        dispatcher = StandardTestDispatcher(testScheduler)
        val store = ActiveServerStore()
        val state = state(store, FakeSettings(mapOf("s1" to true, "s2" to false)))

        store.activate(server("s1"), null)
        advanceUntilIdle()
        assertTrue(state.enabled.value)

        // Per-server isolation: switching must not leak server 1's preference into server 2.
        store.activate(server("s2"), null)
        advanceUntilIdle()
        assertFalse(state.enabled.value)
    }

    @Test
    fun `clearing the active server turns privacy off`() = runTest {
        dispatcher = StandardTestDispatcher(testScheduler)
        val store = ActiveServerStore()
        val state = state(store, FakeSettings(mapOf("s1" to true)))

        store.activate(server("s1"), null)
        advanceUntilIdle()
        assertTrue(state.enabled.value)

        store.clear()
        advanceUntilIdle()
        assertFalse("no active server means no privacy state to honour", state.enabled.value)
    }

    @Test
    fun `enabling via the toggle persists for the active server`() = runTest {
        dispatcher = StandardTestDispatcher(testScheduler)
        val store = ActiveServerStore()
        val settings = FakeSettings(mapOf("s1" to false))
        val state = state(store, settings)
        store.activate(server("s1"), null)
        advanceUntilIdle()
        assertFalse(state.enabled.value)

        state.toggleForActiveServer(true)
        advanceUntilIdle()

        // Optimistic in-memory value...
        assertTrue(state.enabled.value)
        // ...and the durable write, so a cold start comes back with privacy on.
        assertTrue(settings.stored.value["s1"] == true)
    }

    @Test
    fun `toggling without an active server does not throw`() = runTest {
        dispatcher = StandardTestDispatcher(testScheduler)
        val settings = FakeSettings()
        val state = state(ActiveServerStore(), settings)

        // Startup race: the switch can be tapped before a server is active. There is nothing to
        // persist and no server to scope the flag to, so the only requirement is "no crash".
        state.toggleForActiveServer(true)
        advanceUntilIdle()
        assertTrue("nothing to persist without a server", settings.stored.value.isEmpty())
    }
}
