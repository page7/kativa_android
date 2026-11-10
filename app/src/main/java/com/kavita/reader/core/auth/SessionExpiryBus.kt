package com.kavita.reader.core.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Process-wide "this server's session is dead" signal.
 *
 * Emitted from the network layer when a business request comes back 401 while a token was sent.
 * The UI collects it and returns to the server picker with that server marked
 * "expired → sign in again" (docs/FLOWS.md F1 T4).
 *
 * Not emitted for sign-in endpoints: a wrong password is also a 401, and it must stay on the
 * login form instead of bouncing the user around.
 *
 * Backed by a [Channel] rather than a `SharedFlow` on purpose: the first 401 can arrive before
 * the navigation host has subscribed (a restore from background, for instance), and an event
 * dropped by `SharedFlow(replay = 0)` would leave the user on the same dead screen.
 */
@Singleton
class SessionExpiryBus @Inject constructor() {

    private val channel = Channel<String?>(capacity = Channel.BUFFERED)

    val events: Flow<String?> = channel.receiveAsFlow()

    /** @param serverId the server whose session died, or null when the active one is unknown. */
    fun notifyExpired(serverId: String?) {
        channel.trySend(serverId)
    }
}
