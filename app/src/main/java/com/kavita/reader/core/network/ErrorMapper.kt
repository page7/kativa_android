package com.kavita.reader.core.network

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.ConnectException
import javax.net.ssl.SSLException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.IOException

/**
 * Maps everything the network can throw into [KavitaError].
 *
 * Kavita error bodies are localised *plain text* far more often than JSON, so we try JSON first
 * and fall back to the raw string. See AGENTS.md §4.5.
 */
object ErrorMapper {

    private val json = Json { ignoreUnknownKeys = true }

    fun from(throwable: Throwable): KavitaError = when (throwable) {
        is KavitaError -> throwable
        is HttpException -> fromHttp(throwable)
        is UnknownHostException -> KavitaError.Dns
        is SocketTimeoutException -> KavitaError.Timeout
        is ConnectException -> KavitaError.Network
        is SSLException -> KavitaError.Network
        is IOException -> KavitaError.Network
        else -> KavitaError.Unknown
    }

    private fun fromHttp(e: HttpException): KavitaError {
        val message = readMessage(e.response()?.errorBody())
        return when (e.code()) {
            400 -> KavitaError.BadRequest(message)
            401 -> KavitaError.Unauthorized(message)
            403 -> KavitaError.Forbidden(message)
            404 -> KavitaError.NotFound(message)
            408 -> KavitaError.Timeout
            in 500..599 -> KavitaError.Server(e.code(), message)
            else -> KavitaError.Server(e.code(), message)
        }
    }

    /** Extracts a human-readable message from a Kavita error body (JSON or plain text). */
    fun readMessage(body: ResponseBody?): String? {
        if (body == null) return null
        val text = runCatching { body.string() }.getOrNull()?.trim().orEmpty()
        if (text.isEmpty()) return null

        if (text.startsWith("{")) {
            runCatching {
                val obj = json.parseToJsonElement(text) as JsonObject

                // ASP.NET returns `ValidationProblemDetails` with a per-field `errors` map.
                // Surfacing it turns "One or more validation errors occurred" into an actionable
                // message naming the offending field.
                val details = (obj["errors"] as? JsonObject)
                    ?.entries
                    ?.joinToString("; ") { (field, messages) ->
                        val joined = (messages as? kotlinx.serialization.json.JsonArray)
                            ?.joinToString(", ") { it.jsonPrimitive.content }
                            ?: messages.toString()
                        "$field: $joined"
                    }
                    ?.takeIf { it.isNotBlank() }

                if (details != null) return details

                (obj["message"] ?: obj["title"] ?: obj["detail"])?.jsonPrimitive?.content
            }.getOrNull()?.let { if (it.isNotBlank()) return it }
        }

        // Plain-text bodies can be long localisation strings; keep the UI readable.
        return if (text.length > 300) text.take(300) + "…" else text
    }
}

/** Stable, throwable-free wrapper for repository return values. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val failure: KavitaFailure) : Outcome<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value
}

inline fun <T> runCatchingOutcome(block: () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}
