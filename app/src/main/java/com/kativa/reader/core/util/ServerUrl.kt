package com.kativa.reader.core.util

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
        val marker = "apikey="
        val idx = raw.indexOf(marker, ignoreCase = true)
        if (idx < 0) return null
        val value = raw.substring(idx + marker.length)
            .substringBefore('&')
            .substringBefore('#')
            .trim()
        return value.ifEmpty { null }
    }

    /**
     * Rewrites an already-normalised base URL to use the given scheme.
     * Used when the user toggles "allow cleartext" on a saved server.
     */
    fun withScheme(baseUrl: String, https: Boolean): String =
        baseUrl.replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), if (https) "https://" else "http://")
}
