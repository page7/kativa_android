package com.kativa.reader.core.network

import com.kativa.reader.core.auth.ActiveServerStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds one Retrofit instance per server URL and caches it.
 *
 * A single OkHttp client is shared (connection pool, caches, interceptors), but the base URL is
 * fixed per server — which is safer than rewriting URLs, because the token interceptor can never
 * accidentally attach server A's token to server B's host (AGENTS.md §4.1/§4.2).
 */
@Singleton
class ApiFactory @Inject constructor(
    private val activeServerStore: ActiveServerStore,
    private val paginationInterceptor: PaginationInterceptor,
    private val cleartextGate: CleartextGateInterceptor,
    @param:com.kativa.reader.core.di.DebugBuild private val isDebug: Boolean,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(AuthInterceptor(activeServerStore))
            .addInterceptor(cleartextGate)
            .addInterceptor(paginationInterceptor)
            .addInterceptor(DeviceHeaderInterceptor())
            .apply {
                if (isDebug) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                            redactHeader("x-api-key")
                            redactHeader("Cookie")
                        },
                    )
                }
            }
            .build()
    }

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Retrofit>()

    private fun retrofitFor(baseUrl: String): Retrofit = cache.getOrPut(baseUrl) {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType()),
            )
            .build()
    }

    private fun requireBaseUrl(): String =
        activeServerStore.active.value?.baseUrl
            ?: throw IllegalStateException("No active server selected")

    fun account(): com.kativa.reader.data.remote.api.AccountApi =
        retrofitFor(requireBaseUrl()).create(com.kativa.reader.data.remote.api.AccountApi::class.java)

    fun server(): com.kativa.reader.data.remote.api.ServerApi =
        retrofitFor(requireBaseUrl()).create(com.kativa.reader.data.remote.api.ServerApi::class.java)

    fun library(): com.kativa.reader.data.remote.api.LibraryApi =
        retrofitFor(requireBaseUrl()).create(com.kativa.reader.data.remote.api.LibraryApi::class.java)

    fun series(): com.kativa.reader.data.remote.api.SeriesApi =
        retrofitFor(requireBaseUrl()).create(com.kativa.reader.data.remote.api.SeriesApi::class.java)

    fun seriesDetail(): com.kativa.reader.data.remote.api.SeriesDetailApi =
        retrofitFor(requireBaseUrl()).create(com.kativa.reader.data.remote.api.SeriesDetailApi::class.java)

    fun stream(): com.kativa.reader.data.remote.api.StreamApi =
        retrofitFor(requireBaseUrl()).create(com.kativa.reader.data.remote.api.StreamApi::class.java)

    fun reader(): com.kativa.reader.data.remote.api.ReaderApi =
        retrofitFor(requireBaseUrl()).create(com.kativa.reader.data.remote.api.ReaderApi::class.java)

    /** Builds an anonymous client for an arbitrary URL, used by "test connection". */
    fun anonymousServerApi(rawBaseUrl: String): com.kativa.reader.data.remote.api.ServerApi =
        retrofitFor(rawBaseUrl).create(com.kativa.reader.data.remote.api.ServerApi::class.java)

    fun invalidate(baseUrl: String) {
        cache.remove(baseUrl)
    }
}
