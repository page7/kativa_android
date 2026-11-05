package com.kativa.tools.assets

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AssetManifest(
    val version: Int = 1,
    val entries: List<ManifestEntry> = emptyList(),
)

@Serializable
data class ManifestEntry(
    val id: String,
    val kind: String = "icon",
    val width: Int = 24,
    val height: Int = 24,
    val code: String = "",
    @SerialName("status") val status: String = "placeholder",
)

object ManifestJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): AssetManifest {
        // Strip a UTF-8 BOM that PowerShell/Notepad editors like to add.
        val clean = text.removePrefix("\uFEFF")
        return json.decodeFromString(AssetManifest.serializer(), clean)
    }
}
