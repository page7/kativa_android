package com.kavita.reader.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavita.reader.core.auth.SessionExpiryBus
import com.kavita.reader.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Turns a process-wide "session expired" signal into a navigation-safe event.
 *
 * Ordering matters: the stored token is cleared **before** the navigation event is re-emitted, so
 * the server picker the user lands on can never flash "signed in" again (it reads the same store).
 *
 * The ViewModel exists so the collector survives configuration changes and outlives individual
 * screens: the 401 may arrive while the user is deep in the reader.
 */
@HiltViewModel
class SessionExpiryHandler @Inject constructor(
    bus: SessionExpiryBus,
    private val authRepository: AuthRepository,
    private val activeServerStore: com.kavita.reader.core.auth.ActiveServerStore,
) : ViewModel() {

    private val _expired = MutableSharedFlow<String?>(extraBufferCapacity = 4)

    /** Emits once the dead session has been removed from storage. */
    val expired: SharedFlow<String?> = _expired.asSharedFlow()

    /** Server whose credential needs renewing; null when nothing is active. */
    fun serverToSignIn(): String? = activeServerStore.activeId

    init {
        viewModelScope.launch {
            bus.events.collect { expiredServerId ->
                // A dead credential is worthless without a refresh flow; keeping it is exactly
                // what made the picker claim "signed in" while every request failed.
                expiredServerId?.let { authRepository.invalidateSession(it, notify = false) }
                _expired.tryEmit(expiredServerId)
            }
        }
    }
}
