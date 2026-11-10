package com.kavita.reader.core.auth

import com.kavita.reader.domain.model.ServerProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the currently active server for the running process.
 *
 * Every request builder, image loader and cache directory resolves through this, so switching
 * servers cannot leak credentials or covers between them (AGENTS.md §4.1/§4.2).
 */
@Singleton
class ActiveServerStore @Inject constructor() {

    private val _active = MutableStateFlow<ServerProfile?>(null)
    val active: StateFlow<ServerProfile?> = _active.asStateFlow()

    /** API session of the active server, when signed in. */
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    val requireActive: ServerProfile
        get() = _active.value ?: error("No active server")

    fun activate(profile: ServerProfile, session: Session?) {
        _active.value = profile
        _session.value = session
    }

    fun updateSession(session: Session?) {
        _session.value = session
    }

    fun clear() {
        _active.value = null
        _session.value = null
    }

    val activeId: String? get() = _active.value?.id
}

/** Convenience: the session id stream used by ViewModels that must react to sign-out. */
fun ActiveServerStore.sessionFlow(): Flow<Session?> = session
