package com.kavita.reader.data.repository

import com.kavita.reader.core.network.ApiFactory
import com.kavita.reader.core.network.ErrorMapper
import com.kavita.reader.core.network.KavitaError
import com.kavita.reader.core.network.KavitaFailure
import com.kavita.reader.core.network.Outcome
import com.kavita.reader.core.util.ServerUrl
import javax.inject.Inject
import javax.inject.Singleton

/** Probe result for "test connection". */
data class ProbeResult(
    val version: String?,
    val displayUrl: String,
)

/**
 * Server connectivity probe. Uses an anonymous client so it works before sign-in
 * and without an active server context (docs/FLOWS.md F1).
 */
@Singleton
class ServerProbe @Inject constructor(
    private val apiFactory: ApiFactory,
) {
    suspend fun probe(normalized: ServerUrl.Normalized): Outcome<ProbeResult> = try {
        val api = apiFactory.probeServerApi(normalized.baseUrl)

        // Kavita's /Health returns the plain text `Ok`, so read the body directly instead of
        // letting a JSON converter parse it.
        val health = api.health().use { it.string().trim() }
        if (!health.equals("Ok", ignoreCase = true)) {
            Outcome.Failure(KavitaFailure(KavitaError.NotKavita))
        } else {
            // Version is a nice-to-have badge; never fail the probe because of it.
            val version = runCatching { api.serverInfo().kavitaVersion }.getOrNull()
            Outcome.Success(ProbeResult(version = version, displayUrl = normalized.displayUrl))
        }
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }
}
