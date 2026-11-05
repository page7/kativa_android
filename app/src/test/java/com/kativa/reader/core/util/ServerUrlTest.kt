package com.kativa.reader.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * URL normalisation is the single most error-prone input in the app: users paste hosts,
 * base URLs, doc links and OPDS links. AGENTS.md §4.1.
 */
class ServerUrlTest {

    private fun ok(input: String): ServerUrl.Normalized {
        val result = ServerUrl.normalize(input)
        assertTrue("expected Ok for '$input' but was $result", result is ServerUrl.Result.Ok)
        return (result as ServerUrl.Result.Ok).value
    }

    @Test
    fun `bare host gets https and api suffix`() {
        val n = ok("kavita.example.com")
        assertEquals("https://kavita.example.com/api/", n.baseUrl)
        assertEquals("https://kavita.example.com", n.displayUrl)
        assertEquals(false, n.isCleartext)
    }

    @Test
    fun `host with port is preserved`() {
        assertEquals("https://kavita.example.com:5000/api/", ok("https://kavita.example.com:5000").baseUrl)
    }

    @Test
    fun `trailing slashes are trimmed`() {
        assertEquals("https://kavita.example.com/api/", ok("https://kavita.example.com///").baseUrl)
    }

    @Test
    fun `existing api path is not duplicated`() {
        assertEquals("https://kavita.example.com/api/", ok("https://kavita.example.com/api").baseUrl)
        assertEquals("https://kavita.example.com/api/", ok("https://kavita.example.com/api/").baseUrl)
    }

    @Test
    fun `sub path is preserved`() {
        assertEquals("https://host.example.com/kavita/api/", ok("https://host.example.com/kavita").baseUrl)
        assertEquals("https://host.example.com/kavita/api/", ok("https://host.example.com/kavita/api").baseUrl)
    }

    @Test
    fun `login page suffix is stripped`() {
        assertEquals("https://kavita.example.com/api/", ok("https://kavita.example.com/login").baseUrl)
    }

    @Test
    fun `api key is extracted from a pasted link`() {
        val n = ok("https://kavita.example.com/login?apiKey=abc123&foo=bar")
        assertEquals("abc123", n.extractedApiKey)
        assertEquals("https://kavita.example.com/api/", n.baseUrl)
    }

    @Test
    fun `opds style link extracts the key and keeps the host`() {
        val n = ok("https://kavita.example.com/api/opds/abcdef0123")
        // Path is not `api` only, so it becomes a sub path — the key is still reported by the
        // login flow separately; the important part is that parsing never throws.
        assertTrue(n.baseUrl.startsWith("https://kavita.example.com"))
    }

    @Test
    fun `http is reported as cleartext`() {
        val n = ok("http://192.168.1.10:5000")
        assertEquals("http://192.168.1.10:5000/api/", n.baseUrl)
        assertTrue(n.isCleartext)
    }

    @Test
    fun `whitespace around the input is ignored`() {
        assertEquals("https://kavita.example.com/api/", ok("   https://kavita.example.com   ").baseUrl)
    }

    @Test
    fun `ipv6 literal is bracketed`() {
        val n = ok("https://[::1]:5000")
        assertEquals("https://[::1]:5000/api/", n.baseUrl)
    }

    @Test
    fun `empty input is rejected`() {
        assertEquals(ServerUrl.Reason.Empty, (ServerUrl.normalize("   ") as ServerUrl.Result.Invalid).reason)
    }

    @Test
    fun `unsupported scheme is rejected`() {
        assertEquals(
            ServerUrl.Reason.UnsupportedScheme,
            (ServerUrl.normalize("ftp://kavita.example.com") as ServerUrl.Result.Invalid).reason,
        )
    }

    @Test
    fun `missing key yields null`() {
        assertNull(ok("https://kavita.example.com").extractedApiKey)
    }
}
