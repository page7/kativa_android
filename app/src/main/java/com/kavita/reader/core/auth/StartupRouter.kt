package com.kavita.reader.core.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * The single answer to "where does the app go after the splash screen?".
 *
 * Product rule: the app does NOT sign in automatically by default. It only skips the server
 * picker when the user has explicitly nominated a startup server AND that server still has a
 * usable session. See AGENTS.md §1 and docs/FLOWS.md F1.
 */
@Singleton
class StartupRouter @Inject constructor(
    private val activeServerStore: ActiveServerStore,
) {
    sealed interface Destination {
        /** No servers saved at all → welcome screen (logo only), then server setup. */
        data object Welcome : Destination

        /** Servers exist but none is auto-login, or the auto-login one is not signed in. */
        data object ServerPicker : Destination

        /** Auto-login server with a live session → go straight to its home. */
        data object Home : Destination
    }

    fun decide(servers: List<com.kavita.reader.domain.model.ServerProfile>, autoLoginServerId: String?, hasSession: (String) -> Boolean): Destination {
        if (servers.isEmpty()) return Destination.Welcome
        val auto = servers.firstOrNull { it.id == autoLoginServerId } ?: return Destination.ServerPicker
        return if (hasSession(auto.id)) Destination.Home else Destination.ServerPicker
    }

    val activeSession: StateFlow<Session?> get() = activeServerStore.session
}
