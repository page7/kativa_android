package com.kativa.reader.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LibraryDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("type") val type: Int = 0,
    @SerialName("lastScanned") val lastScanned: String? = null,
    @SerialName("coverImage") val coverImage: String? = null,
)
