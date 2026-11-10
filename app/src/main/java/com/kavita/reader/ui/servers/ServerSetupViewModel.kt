package com.kavita.reader.ui.servers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavita.reader.core.auth.ServerRepository
import com.kavita.reader.core.network.KavitaError
import com.kavita.reader.core.network.KavitaFailure
import com.kavita.reader.core.network.Outcome
import com.kavita.reader.core.util.ServerUrl
import com.kavita.reader.data.repository.ServerProbe
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val failure: KavitaFailure? = null,
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
    private val pendingApiKeyStore: com.kavita.reader.core.auth.PendingApiKeyStore,
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
                    // Reflect the stored startup preference: this screen is now the only place the
                    // flag can be changed, so showing it unchecked would silently mislead.
                    val isAutoLogin = serverRepository.autoLoginServerId.first() == id
                    _state.update {
                        it.copy(
                            name = profile.name,
                            url = schemeStripped,
                            allowCleartext = profile.allowCleartext,
                            autoLogin = isAutoLogin,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }

    fun onUrlChange(value: String) = _state.update {
        it.copy(
            url = value,
            testedOk = false,
            testedVersion = null,
            error = null,
            failure = null,
            // A stale "we switched you to http" note must not survive a new address.
            autoSwitchedToCleartext = false,
        )
    }

    fun onAllowCleartextChange(value: Boolean) = _state.update {
        it.copy(allowCleartext = value, testedOk = false, autoSwitchedToCleartext = false)
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
    private fun looksLikeTlsFailure(error: KavitaError): Boolean = when (error) {
        KavitaError.Network, KavitaError.Timeout, KavitaError.Unknown -> true
        else -> false
    }

    /**
     * Persists the server and reports whether a probe ever succeeded, so the caller can decide
     * whether to go straight to sign-in.
     *
     * Saving is intentionally independent of `testedOk`: a user on a flaky network must still be
     * able to store an address and fix credentials later.
     *
     * Scheme safety: a bare `host:port` defaults to `https://`, which is the correct secure
     * default. Self-hosted Kavita frequently serves plain HTTP only, and then *every* request —
     * login included — used to die with `SSLException: Unable to parse TLS packet header`, which
     * surfaced as the useless "Network error. Check your connection." So before saving an
     * unverified https profile we probe it. We only ever fall back when the server actually
     * answers over http, and the fallback is never silent: it flips the per-server cleartext
     * opt-in, shows the warning, and waits for the user to confirm with a second tap
     * (AGENTS.md §4.1 — cleartext must be an explicit, per-server decision).
     */
    fun save(onSaved: (serverId: String, autoLogin: Boolean, probed: Boolean) -> Unit) {
        val normalized = normalizeInput() ?: return
        // An explicit `http://` in the address field is itself an unambiguous opt-in — the user
        // typed it. Without this, the profile was stored as http + allowCleartext=false, and the
        // cleartext gate then blocked *every* request, surfacing as a bogus "Network error".
        val confirmedCleartext = _state.value.allowCleartext || normalized.isCleartext
        if (confirmedCleartext != _state.value.allowCleartext) {
            // Keep the switch honest about what is being saved.
            _state.update { it.copy(allowCleartext = confirmedCleartext) }
        }

        // Already opted in, or the user ran a probe that succeeded: persist as-is.
        if (confirmedCleartext || _state.value.testedOk) {
            viewModelScope.launch {
                persist(normalized, cleartext = confirmedCleartext, testedOk = _state.value.testedOk, onSaved)
            }
            return
        }

        // Unverified https: find out which scheme this server actually speaks before saving one
        // that is known to be wrong.
        viewModelScope.launch {
            _state.update { it.copy(testing = true) }
            val httpsProbe = probe.probe(normalized)
            if (httpsProbe is Outcome.Success) {
                _state.update { it.copy(testing = false) }
                persist(normalized, cleartext = false, testedOk = true, onSaved)
                return@launch
            }

            val discoverable = (httpsProbe as Outcome.Failure).failure.error.let(::looksLikeTlsFailure)
            val http = if (discoverable) {
                probe.probe(
                    normalized.copy(
                        baseUrl = ServerUrl.withScheme(normalized.baseUrl, https = false),
                        displayUrl = ServerUrl.withScheme(normalized.displayUrl, https = false),
                        isCleartext = true,
                    ),
                )
            } else {
                null
            }

            if (http is Outcome.Success) {
                // Plain HTTP confirmed. Do not save behind the user's back: surface the finding and
                // let them confirm the downgrade explicitly.
                _state.update {
                    it.copy(
                        testing = false,
                        testedOk = true,
                        testedVersion = http.value.version,
                        url = http.value.displayUrl,
                        allowCleartext = true,
                        autoSwitchedToCleartext = true,
                        failure = null,
                        error = null,
                    )
                }
            } else {
                // Inconclusive (offline, wrong port, server down): save the secure default and let
                // the user correct it later instead of blocking the flow.
                _state.update { it.copy(testing = false) }
                persist(normalized, cleartext = false, testedOk = false, onSaved)
            }
        }
    }

    private fun normalizeInput(): ServerUrl.Normalized? {
        val allowCleartext = _state.value.allowCleartext
        val normalized = (
            ServerUrl.normalize(
                _state.value.url,
                defaultScheme = if (allowCleartext) "http" else "https",
            ) as? ServerUrl.Result.Ok
            )?.value
        if (normalized == null) {
            _state.update { it.copy(error = ServerUrl.Reason.Malformed) }
        }
        return normalized
    }

    private suspend fun persist(
        normalized: ServerUrl.Normalized,
        cleartext: Boolean,
        testedOk: Boolean,
        onSaved: (serverId: String, autoLogin: Boolean, probed: Boolean) -> Unit,
    ) {
        val id = editingId ?: UUID.randomUUID().toString()
        // Keep the stored scheme in sync with the decision that was actually confirmed.
        val baseUrl = if (cleartext) {
            ServerUrl.withScheme(normalized.baseUrl, https = false)
        } else {
            normalized.baseUrl
        }
        val profile = com.kavita.reader.domain.model.ServerProfile(
            id = id,
            name = _state.value.name.trim(),
            baseUrl = baseUrl,
            allowCleartext = cleartext,
            lastUsedAt = null,
        )
        serverRepository.upsert(profile)
        // The startup flag is a single selection, so it must be able to be turned OFF here too —
        // only setting it when checked left users unable to undo it.
        if (_state.value.autoLogin) {
            serverRepository.setAutoLogin(id)
        } else if (serverRepository.autoLoginServerId.first() == id) {
            serverRepository.setAutoLogin(null)
        }
        // If the address was a pasted OPDS link, carry the recovered key over to the login screen.
        normalized.extractedApiKey?.let { pendingApiKeyStore.put(id, it) }
        onSaved(id, _state.value.autoLogin, testedOk)
    }

    fun describe(failure: KavitaFailure?): KavitaError? = failure?.error
}
