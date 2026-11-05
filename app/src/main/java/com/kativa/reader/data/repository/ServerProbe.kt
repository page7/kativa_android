package com.kativa.reader.data.repository

import com.kativa.reader.core.network.ApiFactory
import com.kativa.reader.core.network.ErrorMapper
import com.kativa.reader.core.network.KativaError
import com.kativa.reader.core.network.KativaFailure
import com.kativa.reader.core.network.Outcome
import com.kativa.reader.core.util.ServerUrl
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
        val api = apiFactory.anonymousServerApi(normalized.baseUrl)
        val health = api.health()
        if (!health.trim().equals("Ok", ignoreCase = true)) {
            Outcome.Failure(KativaFailure(KativaError.NotKavita))
        } else {
            val version = runCatching { api.serverInfo().kavitaVersion }.getOrNull()
            Outcome.Success(ProbeResult(version = version, displayUrl = normalized.displayUrl))
        }
    } catch (t: Throwable) {
        Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
    }
}
