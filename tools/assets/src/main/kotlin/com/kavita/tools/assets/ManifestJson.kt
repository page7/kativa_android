package com.kavita.tools.assets

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
    /**
     * For `launcher-foreground` only: how the artwork fills the 108dp adaptive canvas.
     *
     * `safe` (default) centres the artwork inside the 72dp safe zone, which is right for a mark on
     * a transparent background. `full` draws it edge to edge, which is right for artwork that
     * carries its own full-bleed background — shrinking that into the safe zone would leave a
     * visible square floating inside the launcher's mask.
     */
    val bleed: String = "safe",
)

object ManifestJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): AssetManifest {
        // Strip a UTF-8 BOM that PowerShell/Notepad editors like to add.
        val clean = text.removePrefix("\uFEFF")
        return json.decodeFromString(AssetManifest.serializer(), clean)
    }
}
