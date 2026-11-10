package com.kavita.reader.core.util

import java.net.URI
import java.net.URISyntaxException

/**
 * Normalises whatever the user pasted into a Retrofit base URL.
 *
 * Rules (AGENTS.md §4.1):
 *  - accept `host`, `host:port`, `https://host`, `https://host/api`, trailing slashes, spaces
 *  - accept a full Kavita link containing `?apiKey=…` and report the extracted key
 *  - never double up on `/api/`
 */
object ServerUrl {

    data class Normalized(
        val baseUrl: String,
        val displayUrl: String,
        val extractedApiKey: String?,
        val isCleartext: Boolean,
    )

    sealed interface Result {
        data class Ok(val value: Normalized) : Result
        data class Invalid(val reason: Reason) : Result
    }

    enum class Reason { Empty, Malformed, UnsupportedScheme, MissingHost, BlockedLoopback }

    /**
     * @param defaultScheme scheme used when the user omits one. Pass "http" when the
     *   "allow unencrypted" switch is on, so a plain-HTTP-only server is reachable without
     *   the user having to type the scheme.
     */
    fun normalize(rawInput: String, defaultScheme: String = "https"): Result {
        val raw = rawInput.trim()
        if (raw.isEmpty()) return Result.Invalid(Reason.Empty)

        val extractedKey = extractApiKey(raw)
        val withoutQuery = raw.substringBefore('?').substringBefore('#')
        val withScheme = when {
            withoutQuery.startsWith("http://", ignoreCase = true) -> withoutQuery
            withoutQuery.startsWith("https://", ignoreCase = true) -> withoutQuery
            withoutQuery.contains("://") -> return Result.Invalid(Reason.UnsupportedScheme)
            else -> "$defaultScheme://$withoutQuery"
        }

        val uri = try {
            URI(withScheme)
        } catch (_: URISyntaxException) {
            return Result.Invalid(Reason.Malformed)
        }

        if (uri.scheme !in listOf("http", "https")) return Result.Invalid(Reason.UnsupportedScheme)
        val host = uri.host ?: return Result.Invalid(Reason.MissingHost)
        if (host.isBlank() || host.contains(' ')) return Result.Invalid(Reason.MissingHost)

        var path = uri.path.orEmpty().trimEnd('/')
        // Strip a trailing /api the user may have copied from the docs.
        if (path.endsWith("/api", ignoreCase = true)) path = path.dropLast(4)
        // Strip a trailing /login page.
        if (path.endsWith("/login", ignoreCase = true)) path = path.dropLast(6)
        // Strip a trailing `/api/opds/<key>`: that is how Kavita prints its OPDS feed URL, and it is
        // the link users are most likely to paste. Keeping it would build `/api/opds/<key>/api/`.
        path = path.replace(OPDS_SUFFIX, "")
        if (path.endsWith("/api", ignoreCase = true)) path = path.dropLast(4)

        val port = if (uri.port > 0) ":${uri.port}" else ""
        // URI.host already wraps IPv6 literals in brackets on some JDKs; normalise before rebuilding.
        val bareHost = host.removePrefix("[").removeSuffix("]")
        val authorityHost = if (bareHost.contains(':')) "[$bareHost]" else bareHost
        val root = "${uri.scheme}://$authorityHost$port$path"
        val baseUrl = "$root/api/"

        return Result.Ok(
            Normalized(
                baseUrl = baseUrl,
                displayUrl = root,
                extractedApiKey = extractedKey,
                isCleartext = uri.scheme.equals("http", ignoreCase = true),
            ),
        )
    }

    private fun extractApiKey(raw: String): String? {
        // `?apiKey=...` (docs / login links)
        val marker = "apikey="
        val idx = raw.indexOf(marker, ignoreCase = true)
        if (idx >= 0) {
            val value = raw.substring(idx + marker.length)
                .substringBefore('&')
                .substringBefore('#')
                .trim()
            if (value.isNotEmpty()) return value
        }

        // `/api/opds/<key>` — Kavita's own OPDS feed URL. Users paste this whole link, so the
        // trailing path segment is the key.
        val opds = OPDS_KEY.find(raw)
        return opds?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** `<host>/api/opds/<key>`; the key is the captured trailing segment. */
    private val OPDS_KEY = Regex("""/api/opds/([^/?#\s]+)$""", RegexOption.IGNORE_CASE)

    /** The same segment, matched so it can be removed from a user-supplied path. */
    private val OPDS_SUFFIX = Regex("""/api/opds/[^/?#\s]+$""", RegexOption.IGNORE_CASE)

    /**
     * Rewrites an already-normalised base URL to use the given scheme.
     * Used when the user toggles "allow cleartext" on a saved server.
     */
    fun withScheme(baseUrl: String, https: Boolean): String =
        baseUrl.replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), if (https) "https://" else "http://")
}
