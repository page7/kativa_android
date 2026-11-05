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
    /** True when the probe had to fall back from https to http; shown as a warning. */
    val autoSwitchedToCleartext: Boolean = false,
) {
    /**
     * Saving no longer requires a successful probe first: a user on a flaky network may still
     * want to store the address and fix credentials later. The probe only provides the version
     * badge and the cleartext confirmation; it is never a hard gate.
     */
    val canSave: Boolean get() = url.isNotBlank() && !testing
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
        val allowCleartext = _state.value.allowCleartext
        val normalized = when (
            val result = ServerUrl.normalize(
                _state.value.url,
                defaultScheme = if (allowCleartext) "http" else "https",
            )
        ) {
            is ServerUrl.Result.Invalid -> {
                _state.update { it.copy(error = result.reason, testedOk = false, failure = null) }
                return
            }

            is ServerUrl.Result.Ok -> result.value
        }

        _state.update { it.copy(testing = true, error = null, failure = null) }

        viewModelScope.launch {
            var probeOutcome = probe.probe(normalized)
            var usedCleartext = normalized.isCleartext

            // A very common self-hosted setup: the server only speaks plain HTTP on its port, so
            // an https probe fails during the TLS handshake. Retry over http once and tell the
            // user we did, rather than making them guess the scheme.
            if (probeOutcome is Outcome.Failure &&
                !normalized.isCleartext &&
                looksLikeTlsFailure(probeOutcome.failure.error)
            ) {
                val httpBase = ServerUrl.withScheme(normalized.baseUrl, https = false)
                val retry = probe.probe(
                    normalized.copy(
                        baseUrl = httpBase,
                        // Keep the user-facing address in sync with the URL actually probed.
                        displayUrl = ServerUrl.withScheme(normalized.displayUrl, https = false),
                        isCleartext = true,
                    ),
                )
                if (retry is Outcome.Success) {
                    probeOutcome = retry
                    usedCleartext = true
                }
            }

            when (probeOutcome) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        testing = false,
                        testedOk = true,
                        testedVersion = probeOutcome.value.version,
                        url = probeOutcome.value.displayUrl,
                        allowCleartext = usedCleartext,
                        failure = null,
                        autoSwitchedToCleartext = usedCleartext && !normalized.isCleartext,
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(testing = false, testedOk = false, failure = probeOutcome.failure)
                }
            }
        }
    }

    /**
     * A TLS handshake against a plain-HTTP port surfaces as an SSL error, and Kavita returns
     * nothing useful for a scheme mismatch. Treat transport-level and SSL failures as
     * "maybe the scheme is wrong" candidates so the http retry can run.
     */
    private fun looksLikeTlsFailure(error: KativaError): Boolean = when (error) {
        KativaError.Network, KativaError.Timeout, KativaError.Unknown -> true
        else -> false
    }

    /**
     * Persists the server and reports whether a probe ever succeeded, so the caller can decide
     * whether to go straight to sign-in.
     *
     * Saving is intentionally independent of `testedOk`: a user must always be able to store an
     * address and then attempt credentials, even if the health probe is blocked.
     */
    fun save(onSaved: (serverId: String, autoLogin: Boolean, probed: Boolean) -> Unit) {
        val allowCleartext = _state.value.allowCleartext
        val normalized = (
            ServerUrl.normalize(
                _state.value.url,
                defaultScheme = if (allowCleartext) "http" else "https",
            ) as? ServerUrl.Result.Ok
            )?.value
        if (normalized == null) {
            _state.update { it.copy(error = ServerUrl.Reason.Malformed) }
            return
        }

        val probed = _state.value.testedOk
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
            onSaved(id, _state.value.autoLogin, probed)
        }
    }

    fun describe(failure: KativaFailure?): KativaError? = failure?.error
}
