package com.kativa.reader.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.auth.RememberedAccountStore
import com.kativa.reader.core.auth.ServerRepository
import com.kativa.reader.core.auth.Session
import com.kativa.reader.core.auth.SessionStatus
import com.kativa.reader.core.auth.SessionStore
import com.kativa.reader.core.auth.status
import com.kativa.reader.domain.model.ServerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ServerStatus { SignedIn, NeedsLogin, Expired, Unreachable }

data class ServerItemState(
    val profile: ServerProfile,
    val status: ServerStatus,
    val hasSession: Boolean,
    /** Username remembered from a previous sign-in, shown as a re-login hint. */
    val rememberedUsername: String? = null,
) {
    /** True when tapping the card can go straight to Home. */
    val canEnterDirectly: Boolean get() = status == ServerStatus.SignedIn
}

data class ServerPickerUiState(
    val loading: Boolean = true,
    val items: List<ServerItemState> = emptyList(),
    val autoLoginServerId: String? = null,
) {
    val isEmpty: Boolean get() = !loading && items.isEmpty()
}

/**
 * Maps one stored session to a card status.
 *
 * Extracted as a pure function because this exact decision caused the bug it now guards:
 * the status used to be `sessions.containsKey(id)`, i.e. "a record exists". An expired token
 * still has a record, so the card said "signed in", tapping it skipped the login screen, and the
 * home screen immediately failed with "session expired" — forever.
 */
fun serverStatusFor(session: Session?, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): ServerStatus =
    when (session.status(nowEpochSeconds)) {
        SessionStatus.Missing -> ServerStatus.NeedsLogin
        SessionStatus.Expired -> ServerStatus.Expired
        SessionStatus.Valid -> ServerStatus.SignedIn
    }

@HiltViewModel
class ServerPickerViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val sessionStore: SessionStore,
    private val rememberedAccounts: RememberedAccountStore,
    private val activeServerStore: ActiveServerStore,
    private val authRepository: com.kativa.reader.data.repository.AuthRepository,
    private val readerSettingsStore: com.kativa.reader.core.settings.ReaderSettingsStore,
) : ViewModel() {

    val uiState: StateFlow<ServerPickerUiState> =
        combine(
            serverRepository.servers,
            serverRepository.autoLoginServerId,
            sessionStore.sessions,
            rememberedAccounts.accounts,
        ) { servers, autoId, sessions, accounts ->
            ServerPickerUiState(
                loading = false,
                items = servers
                    .sortedByDescending { it.lastUsedAt ?: 0L }
                    .map { profile ->
                        val session = sessions[profile.id]
                        val status = serverStatusFor(session)
                        ServerItemState(
                            profile = profile,
                            status = status,
                            hasSession = session != null,
                            rememberedUsername = accounts[profile.id]?.username,
                        )
                    },
                autoLoginServerId = autoId,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ServerPickerUiState(),
        )

    /**
     * Activates a server. Returns true when the caller should go straight to Home.
     *
     * An expired/missing session returns false so navigation goes to the login form — this is the
     * escape hatch that did not exist before.
     */
    suspend fun select(serverId: String): Boolean {
        val profile = serverRepository.getServer(serverId) ?: return false
        val session = sessionStore.get(serverId)
        val usable = session?.takeIf { it.status() == SessionStatus.Valid }

        // A dead token must not be activated: every subsequent request would carry it.
        if (session != null && usable == null) {
            sessionStore.clear(serverId)
        }
        activeServerStore.activate(profile, usable)
        serverRepository.touch(profile)
        return usable != null
    }

    fun delete(serverId: String) {
        viewModelScope.launch {
            serverRepository.delete(serverId)
            sessionStore.clear(serverId)
            // Deleting the server also forgets its account: it is the user's "forget this server".
            rememberedAccounts.clear(serverId)
            // ...and its per-series reader settings, which are keyed by server id (AGENTS.md §4.1).
            runCatching { readerSettingsStore.clearServer(serverId) }
            if (activeServerStore.activeId == serverId) activeServerStore.clear()
        }
    }

    fun statusFor(serverId: String): ServerStatus =
        uiState.value.items.firstOrNull { it.profile.id == serverId }?.status ?: ServerStatus.NeedsLogin
}
