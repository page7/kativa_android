package com.kativa.reader.core.network

/**
 * Every failure the UI can see. UI code must never depend on Retrofit/IO exceptions.
 * See AGENTS.md §4.5.
 */
sealed class KativaError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {

    data object Network : KativaError("network")
    data object Timeout : KativaError("timeout")
    data object Dns : KativaError("dns")
    data object NotKavita : KativaError("not-kavita")
    data object CleartextBlocked : KativaError("cleartext-blocked")
    data object Parse : KativaError("parse")
    data object Unknown : KativaError("unknown")

    data class Unauthorized(val serverMessage: String? = null) : KativaError("unauthorized")
    data class Forbidden(val serverMessage: String? = null) : KativaError("forbidden")
    data class NotFound(val serverMessage: String? = null) : KativaError("not-found")
    data class BadRequest(val serverMessage: String? = null) : KativaError("bad-request")
    data class Server(val status: Int, val serverMessage: String? = null) : KativaError("server-$status")

    /** True when retrying the same request could plausibly help. */
    val retryable: Boolean
        get() = when (this) {
            Network, Timeout, Dns -> true
            is Server -> true
            else -> false
        }
}

/** Normalised server failure. [rawMessage] is the plain-text body Kavita usually returns. */
data class KativaFailure(
    val error: KativaError,
    val rawMessage: String? = null,
)
