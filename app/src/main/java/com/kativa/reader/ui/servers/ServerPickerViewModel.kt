package com.kativa.reader.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.auth.ServerRepository
import com.kativa.reader.core.auth.SessionStore
import com.kativa.reader.domain.model.ServerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ServerStatus { SignedIn, NeedsLogin, Unreachable }

data class ServerItemState(
    val profile: ServerProfile,
    val status: ServerStatus,
    val hasSession: Boolean,
)

data class ServerPickerUiState(
    val loading: Boolean = true,
    val items: List<ServerItemState> = emptyList(),
    val autoLoginServerId: String? = null,
    val message: String? = null,
) {
    val isEmpty: Boolean get() = !loading && items.isEmpty()
}

@HiltViewModel
class ServerPickerViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val sessionStore: SessionStore,
    private val activeServerStore: ActiveServerStore,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ServerPickerUiState> =
        combine(
            serverRepository.servers,
            serverRepository.autoLoginServerId,
            sessionStore.sessions,
            message,
        ) { servers, autoId, sessions, msg ->
            ServerPickerUiState(
                loading = false,
                items = servers
                    .sortedByDescending { it.lastUsedAt ?: 0L }
                    .map { profile ->
                        val hasSession = sessions.containsKey(profile.id)
                        ServerItemState(
                            profile = profile,
                            status = if (hasSession) ServerStatus.SignedIn else ServerStatus.NeedsLogin,
                            hasSession = hasSession,
                        )
                    },
                autoLoginServerId = autoId,
                message = msg,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ServerPickerUiState(),
        )

    /** Activates a server. Returns true when the caller should go straight to Home. */
    suspend fun select(serverId: String): Boolean {
        val profile = serverRepository.getServer(serverId) ?: return false
        val session = sessionStore.get(serverId)
        activeServerStore.activate(profile, session)
        serverRepository.touch(profile)
        return session != null
    }

    fun setAutoLogin(serverId: String?) {
        viewModelScope.launch {
            serverRepository.setAutoLogin(serverId)
            message.value = null
        }
    }

    fun delete(serverId: String) {
        viewModelScope.launch {
            serverRepository.delete(serverId)
            sessionStore.clear(serverId)
            if (activeServerStore.activeId == serverId) activeServerStore.clear()
        }
    }

    fun statusFor(serverId: String): ServerStatus =
        uiState.value.items.firstOrNull { it.profile.id == serverId }?.status ?: ServerStatus.NeedsLogin
}
