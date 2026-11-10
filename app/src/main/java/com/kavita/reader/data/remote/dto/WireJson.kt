package com.kavita.reader.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Serialisation contract for every outgoing request body.
 *
 * `encodeDefaults = true` is mandatory, not cosmetic:
 *
 *  - `LoginRequest` declares `username`/`password` with `""` defaults. With Kotlin's default
 *    behaviour those fields are **omitted** when unchanged, so API-key login serialised to
 *    `{"apiKey":"..."}` and Kavita rejected it with
 *    `400 One or more validation errors occurred. {"Username":[...],"Password":[...]}`.
 *  - Filter bodies silently lost `combination` for the same reason.
 *
 * This object exists so tests can assert the exact wire payload without a running server
 * (see `WireSerializationTest`). API clients must use [WireJson.instance] rather than
 * constructing their own `Json` instance.
 */
object WireJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }
}
