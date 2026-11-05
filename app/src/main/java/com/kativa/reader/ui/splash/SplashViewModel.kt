package com.kativa.reader.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.auth.ServerRepository
import com.kativa.reader.core.auth.SessionStore
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
 *  - auto-login + live session → Home
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

            if (auto != null && session != null) {
                activeServerStore.activate(auto, session)
                _destination.value = SplashDestination.Home
            } else {
                _destination.value = SplashDestination.ServerPicker
            }
        }
    }
}
