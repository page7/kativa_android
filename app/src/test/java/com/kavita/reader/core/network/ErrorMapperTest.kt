package com.kavita.reader.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response as RetrofitResponse

/**
 * Kavita returns localised *plain text* for most failures, and JSON only sometimes.
 * The mapper must never lose the message and never surface raw exceptions (AGENTS.md §4.5).
 */
class ErrorMapperTest {

    private fun http(code: Int, body: String, contentType: String = "text/plain"): HttpException {
        val raw = Response.Builder()
            .request(okhttp3.Request.Builder().url("https://host/api/x").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("msg")
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()
        return HttpException(RetrofitResponse.error<Any>(body.toResponseBody(contentType.toMediaType()), raw))
    }

    @Test
    fun `plain text body is preserved`() {
        val error = ErrorMapper.from(http(401, "Your credentials are not correct"))
        assertTrue(error is com.kavita.reader.core.network.KavitaError.Unauthorized)
        assertEquals("Your credentials are not correct", (error as com.kavita.reader.core.network.KavitaError.Unauthorized).serverMessage)
    }

    @Test
    fun `json body message is extracted`() {
        val error = ErrorMapper.from(http(400, """{"message":"Name is required"}""", "application/json"))
        assertTrue(error is com.kavita.reader.core.network.KavitaError.BadRequest)
        assertEquals("Name is required", (error as com.kavita.reader.core.network.KavitaError.BadRequest).serverMessage)
    }

    @Test
    fun `long plain text is truncated`() {
        val long = "x".repeat(400)
        val error = ErrorMapper.from(http(400, long)) as com.kavita.reader.core.network.KavitaError.BadRequest
        assertTrue(error.serverMessage!!.length <= 301)
        assertTrue(error.serverMessage!!.endsWith("…"))
    }

    @Test
    fun `server errors keep the status code`() {
        val error = ErrorMapper.from(http(503, "Service Unavailable"))
        assertEquals(503, (error as com.kavita.reader.core.network.KavitaError.Server).status)
        assertTrue(error.retryable)
    }

    @Test
    fun `unknown host maps to dns`() {
        val error = ErrorMapper.from(java.net.UnknownHostException("nope"))
        assertEquals(com.kavita.reader.core.network.KavitaError.Dns, error)
    }

    @Test
    fun `socket timeout maps to timeout`() {
        assertEquals(
            com.kavita.reader.core.network.KavitaError.Timeout,
            ErrorMapper.from(java.net.SocketTimeoutException()),
        )
    }

    @Test
    fun `io failure maps to network`() {
        assertEquals(
            com.kavita.reader.core.network.KavitaError.Network,
            ErrorMapper.from(java.io.IOException("boom")),
        )
    }

    @Test
    fun `outcome wraps failures without throwing`() {
        val outcome = com.kavita.reader.core.network.runCatchingOutcome<String> {
            throw java.net.UnknownHostException("nope")
        }
        assertTrue(outcome is com.kavita.reader.core.network.Outcome.Failure)
    }
}
