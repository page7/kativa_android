package com.kavita.reader.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavita.reader.core.auth.ActiveServerStore
import com.kavita.reader.core.auth.ServerRepository
import com.kavita.reader.core.auth.SessionStatus
import com.kavita.reader.core.auth.SessionStore
import com.kavita.reader.core.auth.status
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class SplashDestination { Pending, Welcome, ServerPicker, Home }

/**
 * Startup routing (docs/FLOWS.md F1):
 *  - no servers                → Welcome (logo only)
 *  - servers but no auto-login → ServerPicker (the default behaviour)
 *  - auto-login + usable session → Home
 *
 * Expired sessions are dropped first, so "auto-login + dead token" can never take the user to a
 * home screen that only shows "session expired".
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val sessionStore: SessionStore,
    private val activeServerStore: ActiveServerStore,
) : ViewModel() {

    private val _destination = MutableStateFlow(SplashDestination.Pending)
    val destination: StateFlow<SplashDestination> = _destination.asStateFlow()

    private var resolved = false

    fun resolve() {
        if (resolved) return
        resolved = true

        viewModelScope.launch {
            val servers = serverRepository.getServers()
            if (servers.isEmpty()) {
                _destination.value = SplashDestination.Welcome
                return@launch
            }

            val autoId = serverRepository.autoLoginServerId.first()
            val auto = servers.firstOrNull { it.id == autoId }
            val session = auto?.let { sessionStore.get(it.id) }
            val usable = session?.takeIf { it.status() == SessionStatus.Valid }

            if (auto != null && usable != null) {
                activeServerStore.activate(auto, usable)
                _destination.value = SplashDestination.Home
            } else {
                _destination.value = SplashDestination.ServerPicker
            }
        }
    }
}
