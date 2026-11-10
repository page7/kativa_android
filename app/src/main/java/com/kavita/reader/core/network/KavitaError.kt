package com.kavita.reader.core.network

/**
 * Every failure the UI can see. UI code must never depend on Retrofit/IO exceptions.
 * See AGENTS.md §4.5.
 */
sealed class KavitaError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {

    data object Network : KavitaError("network")
    data object Timeout : KavitaError("timeout")
    data object Dns : KavitaError("dns")
    data object NotKavita : KavitaError("not-kavita")
    data object CleartextBlocked : KavitaError("cleartext-blocked")
    data object Parse : KavitaError("parse")
    data object Unknown : KavitaError("unknown")

    data class Unauthorized(val serverMessage: String? = null) : KavitaError("unauthorized")
    data class Forbidden(val serverMessage: String? = null) : KavitaError("forbidden")
    data class NotFound(val serverMessage: String? = null) : KavitaError("not-found")
    data class BadRequest(val serverMessage: String? = null) : KavitaError("bad-request")
    data class Server(val status: Int, val serverMessage: String? = null) : KavitaError("server-$status")

    /** True when retrying the same request could plausibly help. */
    val retryable: Boolean
        get() = when (this) {
            Network, Timeout, Dns -> true
            is Server -> true
            else -> false
        }
}

/** Normalised server failure. [rawMessage] is the plain-text body Kavita usually returns. */
data class KavitaFailure(
    val error: KavitaError,
    val rawMessage: String? = null,
)
