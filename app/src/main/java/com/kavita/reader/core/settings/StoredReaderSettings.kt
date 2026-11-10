package com.kavita.reader.core.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The persisted subset of the reader settings.
 *
 * Enum fields are stored as **Kavita's numeric `apiValue`s** (not Kotlin enum names) so a future
 * server-sync of the same values needs no migration, and so a renamed Kotlin enum cannot silently
 * invalidate a stored preference. Unknown numbers decode to the reader's own fallback
 * (`ReaderSettingsPersistence`), never throw.
 *
 * [ReaderSettingsCodec] owns the JSON shape; keep this class free of Android and Compose types so
 * the codec stays JVM-testable.
 */
@Serializable
data class StoredReaderSettings(
    val readingDirection: Int = 0,
    val readerMode: Int = 0,
    val layoutMode: Int = 1,
    val scalingOption: Int = 0,
    val coverOffset: Boolean = true,
    val backgroundColor: String = "#000000",
    val showPageNumber: Boolean = true,
    val volumeKeyPaging: Boolean = false,
    val forceLandscape: Boolean = false,
)

/**
 * Encoding for [StoredReaderSettings], plus the three-level fallback chain.
 *
 * Kept separate from `ReaderSettingsStore` (which needs a `Context`) precisely so the two rules
 * that matter can be unit-tested on the JVM: what hits the disk, and which layer wins.
 */
object ReaderSettingsCodec {

    /**
     * `encodeDefaults = true` so an all-default object still round-trips field-for-field —
     * otherwise "the user explicitly set double-page" and "no stored value" would both decode to
     * the same thing and the global layer could never be distinguished from "unset".
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(value: StoredReaderSettings): String = json.encodeToString(StoredReaderSettings.serializer(), value)

    /**
     * A corrupt or truncated entry is treated as "not set" rather than crashing the reader: the
     * fallback chain then supplies a sane value and the user simply sees defaults for that title.
     */
    fun decode(raw: String?): StoredReaderSettings? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(StoredReaderSettings.serializer(), raw) }.getOrNull()
    }
}

/**
 * Series → global default → app default.
 *
 * The user asked for exactly this: a per-title value when one exists, otherwise the value they set
 * once on the settings screen, otherwise the shipped default.
 */
object ReaderSettingsLayers {
    val AppDefault = StoredReaderSettings()

    fun resolve(
        series: StoredReaderSettings?,
        global: StoredReaderSettings?,
    ): StoredReaderSettings = series ?: global ?: AppDefault
}
