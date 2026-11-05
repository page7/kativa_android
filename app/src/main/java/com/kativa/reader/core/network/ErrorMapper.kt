package com.kativa.reader.core.network

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
 * Maps everything the network can throw into [KativaError].
 *
 * Kavita error bodies are localised *plain text* far more often than JSON, so we try JSON first
 * and fall back to the raw string. See AGENTS.md §4.5.
 */
object ErrorMapper {

    private val json = Json { ignoreUnknownKeys = true }

    fun from(throwable: Throwable): KativaError = when (throwable) {
        is KativaError -> throwable
        is HttpException -> fromHttp(throwable)
        is UnknownHostException -> KativaError.Dns
        is SocketTimeoutException -> KativaError.Timeout
        is ConnectException -> KativaError.Network
        is SSLException -> KativaError.Network
        is IOException -> KativaError.Network
        else -> KativaError.Unknown
    }

    private fun fromHttp(e: HttpException): KativaError {
        val message = readMessage(e.response()?.errorBody())
        return when (e.code()) {
            400 -> KativaError.BadRequest(message)
            401 -> KativaError.Unauthorized(message)
            403 -> KativaError.Forbidden(message)
            404 -> KativaError.NotFound(message)
            408 -> KativaError.Timeout
            in 500..599 -> KativaError.Server(e.code(), message)
            else -> KativaError.Server(e.code(), message)
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
    data class Failure(val failure: KativaFailure) : Outcome<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value
}

inline fun <T> runCatchingOutcome(block: () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (t: Throwable) {
        Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
    }

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}
