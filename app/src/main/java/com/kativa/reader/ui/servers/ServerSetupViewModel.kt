package com.kativa.reader.ui.servers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kativa.reader.core.auth.ServerRepository
import com.kativa.reader.core.network.KativaError
import com.kativa.reader.core.network.KativaFailure
import com.kativa.reader.core.network.Outcome
import com.kativa.reader.core.util.ServerUrl
import com.kativa.reader.data.repository.ServerProbe
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ServerSetupUiState(
    val name: String = "",
    val url: String = "",
    val allowCleartext: Boolean = false,
    val autoLogin: Boolean = false,
    val testing: Boolean = false,
    val testedVersion: String? = null,
    val testedOk: Boolean = false,
    val error: ServerUrl.Reason? = null,
    val failure: KativaFailure? = null,
    val editingExisting: Boolean = false,
) {
    val canSave: Boolean get() = testedOk && !testing
}

@HiltViewModel
class ServerSetupViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val probe: ServerProbe,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editingId: String? = savedStateHandle.get<String>("serverId")

    private val _state = MutableStateFlow(ServerSetupUiState(editingExisting = editingId != null))
    val state: StateFlow<ServerSetupUiState> = _state.asStateFlow()

    init {
        editingId?.let { id ->
            viewModelScope.launch {
                serverRepository.getServer(id)?.let { profile ->
                    val schemeStripped = profile.baseUrl
                        .removeSuffix("api/")
                        .trimEnd('/')
                    _state.update {
                        it.copy(
                            name = profile.name,
                            url = schemeStripped,
                            allowCleartext = profile.allowCleartext,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, testedOk = false) }

    fun onUrlChange(value: String) = _state.update {
        it.copy(url = value, testedOk = false, testedVersion = null, error = null, failure = null)
    }

    fun onAllowCleartextChange(value: Boolean) = _state.update {
        it.copy(allowCleartext = value, testedOk = false)
    }

    fun onAutoLoginChange(value: Boolean) = _state.update { it.copy(autoLogin = value) }

    fun test() {
        val normalized = when (val result = ServerUrl.normalize(_state.value.url)) {
            is ServerUrl.Result.Invalid -> {
                _state.update { it.copy(error = result.reason, testedOk = false, failure = null) }
                return
            }

            is ServerUrl.Result.Ok -> result.value
        }

        _state.update { it.copy(testing = true, error = null, failure = null) }

        viewModelScope.launch {
            when (val outcome = probe.probe(normalized)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        testing = false,
                        testedOk = true,
                        testedVersion = outcome.value.version,
                        url = outcome.value.displayUrl,
                        allowCleartext = normalized.isCleartext,
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(testing = false, testedOk = false, failure = outcome.failure)
                }
            }
        }
    }

    /** Persists the server. Returns the server id on success. */
    fun save(onSaved: (serverId: String, autoLogin: Boolean) -> Unit) {
        val normalized = (ServerUrl.normalize(_state.value.url) as? ServerUrl.Result.Ok)?.value
            ?: return
        if (!_state.value.testedOk) return

        viewModelScope.launch {
            val id = editingId ?: UUID.randomUUID().toString()
            val profile = com.kativa.reader.domain.model.ServerProfile(
                id = id,
                name = _state.value.name.trim(),
                baseUrl = normalized.baseUrl,
                allowCleartext = normalized.isCleartext,
                lastUsedAt = null,
            )
            serverRepository.upsert(profile)
            if (_state.value.autoLogin) serverRepository.setAutoLogin(id)
            onSaved(id, _state.value.autoLogin)
        }
    }

    fun describe(failure: KativaFailure?): KativaError? = failure?.error
}
