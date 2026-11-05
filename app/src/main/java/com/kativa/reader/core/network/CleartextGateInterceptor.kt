package com.kativa.reader.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Rejects `http://` requests for servers that have not explicitly opted in.
 *
 * The platform network-security-config allows cleartext globally so a per-server opt-in
 * is possible; this interceptor is the actual gate (AGENTS.md §4.1).
 *
 * Exception: an explicit, per-request opt-in header. The "test connection" probe runs before any
 * server is active, so it cannot consult the active server's flag — but the user typed the address
 * into the setup screen and the probe tells them when it falls back to http.
 */
class CleartextGateInterceptor(
    private val isCleartextAllowed: () -> Boolean,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val explicitlyOptedIn = request.header(HEADER_PROBE_CLEARTEXT) == "1"
        if (request.url.scheme.equals("http", ignoreCase = true) &&
            !isCleartextAllowed() &&
            !explicitlyOptedIn
        ) {
            throw IOException(KativaError.CleartextBlocked.message)
        }
        return chain.proceed(request)
    }

    companion object {
        /** Set by the connection probe only; never sent by normal API traffic. */
        const val HEADER_PROBE_CLEARTEXT = "X-Kativa-Probe-Cleartext"
    }
}
