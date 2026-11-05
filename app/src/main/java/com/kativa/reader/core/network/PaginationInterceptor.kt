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
 * Retrofit cannot see headers while deserialising the body, so we capture them per request
 * into a holder the repository reads right after the call returns. See AGENTS.md §4.3.
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
