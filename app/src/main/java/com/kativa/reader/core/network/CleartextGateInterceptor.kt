package com.kativa.reader.core.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rejects `http://` requests for servers that have not explicitly opted in.
 *
 * The platform network-security-config allows cleartext globally so a per-server opt-in
 * is possible; this interceptor is the actual gate (AGENTS.md §4.1).
 */
class CleartextGateInterceptor(
    private val isCleartextAllowed: () -> Boolean,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.scheme.equals("http", ignoreCase = true) && !isCleartextAllowed()) {
            throw IOException(KativaError.CleartextBlocked.message)
        }
        return chain.proceed(request)
    }
}
