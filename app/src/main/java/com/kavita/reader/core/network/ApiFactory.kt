package com.kavita.reader.core.network

import com.kavita.reader.core.auth.ActiveServerStore
import com.kavita.reader.core.network.CleartextGateInterceptor.Companion.HEADER_PROBE_CLEARTEXT
import com.kavita.reader.data.remote.api.ServerApi
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Interceptor
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
    private val sessionExpiryInterceptor: SessionExpiryInterceptor,
    @param:com.kavita.reader.core.di.DebugBuild private val isDebug: Boolean,
) {
    /**
     * Single source of truth for wire serialisation. See [com.kavita.reader.data.remote.dto.WireJson]
     * for why `encodeDefaults` is mandatory.
     */
    private val json = com.kavita.reader.data.remote.dto.WireJson.instance

    /**
     * Builds the shared OkHttp client.
     *
     * Interceptor order matters: application interceptors run in the order they were added, so
     * anything that must influence the cleartext gate has to come *before* it.
     */
    private fun buildClient(leading: List<Interceptor> = emptyList()): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply { leading.forEach { interceptor -> addInterceptor(interceptor) } }
            .addInterceptor(AuthInterceptor(activeServerStore))
            // After auth so it can see the Authorization header, before everything that consumes
            // the body. Turns a dead token into a navigable "sign in again" event.
            .addInterceptor(sessionExpiryInterceptor)
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

    private val client: OkHttpClient by lazy { buildClient() }

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

    /**
     * Shared client for Coil.
     *
     * Cover/reader URLs already carry `?apiKey=`, so no extra auth work is needed here — but the
     * cleartext gate must still apply, otherwise an http-only server's images would be blocked
     * while its API worked.
     */
    fun imageHttpClient(): okhttp3.OkHttpClient = client

    fun account(): com.kavita.reader.data.remote.api.AccountApi =
        retrofitFor(requireBaseUrl()).create(com.kavita.reader.data.remote.api.AccountApi::class.java)

    fun server(): com.kavita.reader.data.remote.api.ServerApi =
        retrofitFor(requireBaseUrl()).create(com.kavita.reader.data.remote.api.ServerApi::class.java)

    fun library(): com.kavita.reader.data.remote.api.LibraryApi =
        retrofitFor(requireBaseUrl()).create(com.kavita.reader.data.remote.api.LibraryApi::class.java)

    fun series(): com.kavita.reader.data.remote.api.SeriesApi =
        retrofitFor(requireBaseUrl()).create(com.kavita.reader.data.remote.api.SeriesApi::class.java)

    fun seriesDetail(): com.kavita.reader.data.remote.api.SeriesDetailApi =
        retrofitFor(requireBaseUrl()).create(com.kavita.reader.data.remote.api.SeriesDetailApi::class.java)

    fun stream(): com.kavita.reader.data.remote.api.StreamApi =
        retrofitFor(requireBaseUrl()).create(com.kavita.reader.data.remote.api.StreamApi::class.java)

    fun reader(): com.kavita.reader.data.remote.api.ReaderApi =
        retrofitFor(requireBaseUrl()).create(com.kavita.reader.data.remote.api.ReaderApi::class.java)

    fun search(): com.kavita.reader.data.remote.api.SearchApi =
        retrofitFor(requireBaseUrl()).create(com.kavita.reader.data.remote.api.SearchApi::class.java)

    fun collections(): com.kavita.reader.data.remote.api.CollectionApi =
        retrofitFor(requireBaseUrl()).create(com.kavita.reader.data.remote.api.CollectionApi::class.java)

    fun readingLists(): com.kavita.reader.data.remote.api.ReadingListApi =
        retrofitFor(requireBaseUrl()).create(com.kavita.reader.data.remote.api.ReadingListApi::class.java)

    fun wantToRead(): com.kavita.reader.data.remote.api.WantToReadApi =
        retrofitFor(requireBaseUrl()).create(com.kavita.reader.data.remote.api.WantToReadApi::class.java)

    /** Builds an anonymous client for an arbitrary URL, used by "test connection". */
    fun anonymousServerApi(rawBaseUrl: String): com.kavita.reader.data.remote.api.ServerApi =
        retrofitFor(rawBaseUrl).create(com.kavita.reader.data.remote.api.ServerApi::class.java)

    /**
     * Probe client that may target a cleartext URL before any server is active.
     *
     * The per-request header is consumed by [CleartextGateInterceptor]; it is scoped to this
     * probe client so ordinary API traffic can never bypass the gate.
     */
    fun probeServerApi(rawBaseUrl: String): com.kavita.reader.data.remote.api.ServerApi {
        // Leading interceptor: it must run BEFORE the cleartext gate, otherwise the gate sees a
        // request without the opt-in header and rejects the http retry.
        val optIn = Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header(HEADER_PROBE_CLEARTEXT, "1")
                    .build(),
            )
        }

        return Retrofit.Builder()
            .baseUrl(rawBaseUrl)
            .client(buildClient(leading = listOf(optIn)))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(com.kavita.reader.data.remote.api.ServerApi::class.java)
    }

    fun invalidate(baseUrl: String) {
        cache.remove(baseUrl)
    }
}
