package com.kativa.reader.core.network

import com.kativa.reader.core.auth.ActiveServerStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer <jwt>` from the active server's session only.
 *
 * A missing session is not an error here: anonymous endpoints (Health, login) must still work.
 */
class AuthInterceptor(
    private val activeServerStore: ActiveServerStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val session = activeServerStore.session.value
        val request = if (session == null) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer ${session.token}")
                .build()
        }
        return chain.proceed(request)
    }
}

/** Identifies this client to Kavita's device tracking without leaking anything personal. */
class DeviceHeaderInterceptor(
    private val deviceIdProvider: () -> String = { DeviceIdentity.value },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-Device-Id", deviceIdProvider())
            .header("X-Kavita-Client", "kativa-android")
            .header("User-Agent", UserAgent.value)
            .build()
        return chain.proceed(request)
    }
}

internal object DeviceIdentity {
    val value: String by lazy {
        java.util.UUID.randomUUID().toString()
    }
}

internal object UserAgent {
    val value: String by lazy {
        "Kativa/${com.kativa.reader.BuildConfig.VERSION_NAME} (Android ${android.os.Build.VERSION.SDK_INT}; Mobile)"
    }
}
