package com.kativa.reader.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Kavita returns paged results as a bare JSON array; the page metadata lives only in the
 * `Pagination` response header:
 *
 * ```json
 * {"currentPage":1,"itemsPerPage":40,"totalItems":1234,"totalPages":31}
 * ```
 *
 * ⚠️ Do NOT rely on this interceptor for reading metadata. Its `ThreadLocal` only works when the
 * caller runs on the same thread OkHttp used, which is false for Retrofit `suspend` calls: OkHttp
 * writes on its dispatcher thread while the coroutine resumes elsewhere, so [consume] returns
 * `null`. That silently made every folder tile report its page size as the library total.
 *
 * The supported way to read the header is to declare the API method as
 * `retrofit2.Response<List<T>>` and read `response.headers()["Pagination"]` (see
 * `LibraryRepository.seriesPageInLibrary`). This interceptor is kept only because it is harmless
 * and its unit test documents the wire format.
 */
class PaginationInterceptor : Interceptor {

    private val lastPage = ThreadLocal<Pagination?>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        lastPage.set(parse(response.headers["Pagination"]))
        return response
    }

    /** Reads and clears the pagination captured for the most recent call on this thread. */
    fun consume(): Pagination? = lastPage.get().also { lastPage.remove() }

    fun clear() = lastPage.remove()

    private fun parse(header: String?): Pagination? {
        if (header.isNullOrBlank()) return null
        return runCatching {
            val obj = Json.parseToJsonElement(header) as JsonObject
            fun int(key: String): Int = obj[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            Pagination(
                currentPage = int("currentPage"),
                itemsPerPage = int("itemsPerPage"),
                totalItems = int("totalItems"),
                totalPages = int("totalPages"),
            )
        }.getOrNull()
    }

    data class Pagination(
        val currentPage: Int,
        val itemsPerPage: Int,
        val totalItems: Int,
        val totalPages: Int,
    )

    private companion object {
        val Json = Json { ignoreUnknownKeys = true }
    }
}
