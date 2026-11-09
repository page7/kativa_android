package com.kativa.reader.core.network

import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.auth.SessionExpiryBus
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Turns a 401 on an authenticated request into a "session expired" signal.
 *
 * This is the missing half of AGENTS.md §4.5: the app used to surface the raw 401 copy ("your
 * session expired, sign in again") on whatever screen the user happened to be on, with no way to
 * actually reach the login form — the stored token stayed in place, so the picker kept claiming
 * "signed in" and re-entered the same dead session.
 *
 * Only requests that carried an `Authorization` header are considered: a 401 on an anonymous call
 * is not a session problem, and `Account/login`/`Plugin/authenticate` must keep their 401 on the
 * login form (wrong password is also a 401).
 */
class SessionExpiryInterceptor(
    private val activeServerStore: ActiveServerStore,
    private val bus: SessionExpiryBus,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 401 &&
            request.header("Authorization") != null &&
            !isAuthEndpoint(request.url.encodedPath)
        ) {
            bus.notifyExpired(activeServerStore.activeId)
        }
        return response
    }

    private fun isAuthEndpoint(path: String): Boolean =
        path.endsWith("/Account/login", ignoreCase = true) ||
            path.endsWith("/Account/refresh-token", ignoreCase = true) ||
            path.contains("/Plugin/authenticate", ignoreCase = true)
}
