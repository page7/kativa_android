package com.kativa.reader.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.auth.ServerRepository
import com.kativa.reader.core.auth.Session
import com.kativa.reader.core.network.KativaFailure
import com.kativa.reader.core.network.Outcome
import com.kativa.reader.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginMode { Password, ApiKey }

data class LoginUiState(
    val mode: LoginMode = LoginMode.Password,
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val submitting: Boolean = false,
    val failure: KativaFailure? = null,
    val serverName: String = "",
    val serverVersion: String? = null,
) {
    val canSubmit: Boolean
        get() = !submitting && when (mode) {
            LoginMode.Password -> username.isNotBlank() && password.isNotBlank()
            LoginMode.ApiKey -> apiKey.isNotBlank()
        }
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val serverRepository: ServerRepository,
    private val activeServerStore: ActiveServerStore,
    private val pendingApiKeyStore: com.kativa.reader.core.auth.PendingApiKeyStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = savedStateHandle.get<String>("serverId")
        ?: error("serverId is required for the login route")

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = serverRepository.getServer(serverId)
            // Activating here is safe: this screen is only reachable for a chosen server.
            if (profile != null) {
                activeServerStore.activate(profile, activeServerStore.session.value)
            }
            // A pasted OPDS link carries its API key; pre-select that tab and pre-fill it so the
            // user does not have to hunt for the key again (AGENTS.md §4.1).
            val pendingKey = pendingApiKeyStore.consume(serverId)
            _state.update {
                it.copy(
                    serverName = profile?.displayName.orEmpty(),
                    mode = if (pendingKey != null) LoginMode.ApiKey else it.mode,
                    apiKey = pendingKey ?: it.apiKey,
                )
            }
        }
    }

    fun onModeChange(mode: LoginMode) = _state.update { it.copy(mode = mode, failure = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, failure = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, failure = null) }
    fun onApiKeyChange(value: String) = _state.update { it.copy(apiKey = value, failure = null) }

    fun submit(onSuccess: (Session) -> Unit) {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(submitting = true, failure = null) }

        viewModelScope.launch {
            val outcome = when (current.mode) {
                LoginMode.Password -> authRepository.loginWithPassword(
                    username = current.username.trim(),
                    password = current.password,
                )

                LoginMode.ApiKey -> authRepository.loginWithApiKey(current.apiKey.trim())
            }

            when (outcome) {
                is Outcome.Success -> {
                    activeServerStore.active.value?.let { serverRepository.touch(it) }
                    _state.update { it.copy(submitting = false) }
                    onSuccess(outcome.value)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(submitting = false, failure = outcome.failure)
                }
            }
        }
    }
}
