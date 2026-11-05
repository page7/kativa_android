package com.kativa.reader.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `Pagination` header is the only place Kavita reports page metadata, and it is easy to lose
 * because the body is a bare array (AGENTS.md §4.3).
 */
class PaginationInterceptorTest {

    private fun run(interceptor: PaginationInterceptor, header: String?): Response {
        val request = Request.Builder().url("https://host/api/Series/all-v2").build()
        // A hand-rolled chain keeps this a pure unit test (no server, no dispatcher).
        val chain = object : okhttp3.Interceptor.Chain {
            override fun request(): Request = request
            override fun proceed(request: Request): Response = buildResponse(request, header)
            override fun connection() = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 0
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 0
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }
        return interceptor.intercept(chain)
    }

    private fun buildResponse(request: Request, header: String?): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .apply { if (header != null) header("Pagination", header) }
            .body("[]".toResponseBody("application/json".toMediaType()))
            .build()

    @Test
    fun `header is parsed into a pagination object`() {
        val interceptor = PaginationInterceptor()
        run(interceptor, """{"currentPage":2,"itemsPerPage":40,"totalItems":1234,"totalPages":31}""")
        val parsed = interceptor.consume()

        assertEquals(2, parsed?.currentPage)
        assertEquals(40, parsed?.itemsPerPage)
        assertEquals(1234, parsed?.totalItems)
        assertEquals(31, parsed?.totalPages)
    }

    @Test
    fun `missing header yields null rather than throwing`() {
        val interceptor = PaginationInterceptor()
        val response = run(interceptor, null)

        assertTrue(response.isSuccessful)
        assertNull(interceptor.consume())
    }

    @Test
    fun `malformed header yields null`() {
        val interceptor = PaginationInterceptor()
        run(interceptor, "not-json")
        assertNull(interceptor.consume())
    }

    @Test
    fun `consume clears the value so the next call cannot read a stale header`() {
        val interceptor = PaginationInterceptor()
        run(interceptor, """{"currentPage":1,"itemsPerPage":40,"totalItems":40,"totalPages":1}""")
        assertEquals(1, interceptor.consume()?.totalPages)
        assertNull(interceptor.consume())
    }
}
