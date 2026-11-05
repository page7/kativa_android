package com.kativa.reader.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for the M0 surface. Field names mirror Kavita v0.9.1 exactly;
 * every property is explicit so we never rely on a naming strategy.
 */

@Serializable
data class LoginRequest(
    @SerialName("username") val username: String = "",
    @SerialName("password") val password: String = "",
    @SerialName("apiKey") val apiKey: String? = null,
)

@Serializable
data class AuthKeyDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("key") val key: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("createdAtUtc") val createdAtUtc: String? = null,
    @SerialName("expiresAtUtc") val expiresAtUtc: String? = null,
    @SerialName("lastAccessedAtUtc") val lastAccessedAtUtc: String? = null,
    @SerialName("provider") val provider: Int = 0,
)

@Serializable
data class UserDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("username") val username: String = "",
    @SerialName("email") val email: String? = null,
    @SerialName("roles") val roles: List<String> = emptyList(),
    @SerialName("token") val token: String? = null,
    @SerialName("refreshToken") val refreshToken: String? = null,
    @SerialName("apiKey") val apiKey: String? = null,
    @SerialName("authKeys") val authKeys: List<AuthKeyDto> = emptyList(),
    @SerialName("kavitaVersion") val kavitaVersion: String? = null,
    @SerialName("primaryColor") val primaryColor: String? = null,
    @SerialName("secondaryColor") val secondaryColor: String? = null,
)

@Serializable
data class TokenRequestDto(
    @SerialName("token") val token: String,
    @SerialName("refreshToken") val refreshToken: String,
)

@Serializable
data class CreateAuthKeyRequest(
    @SerialName("name") val name: String,
    @SerialName("keyLength") val keyLength: Int = 32,
    @SerialName("expiresUtc") val expiresUtc: String? = null,
)

@Serializable
data class ServerInfoSlimDto(
    @SerialName("installId") val installId: String = "",
    @SerialName("isDocker") val isDocker: Boolean = false,
    @SerialName("kavitaVersion") val kavitaVersion: String = "",
    @SerialName("firstInstallDate") val firstInstallDate: String? = null,
    @SerialName("firstInstallVersion") val firstInstallVersion: String? = null,
)

@Serializable
data class SeriesDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("localizedName") val localizedName: String? = null,
    @SerialName("sortName") val sortName: String? = null,
    @SerialName("pages") val pages: Int = 0,
    @SerialName("pagesRead") val pagesRead: Int = 0,
    @SerialName("libraryId") val libraryId: Int = 0,
    @SerialName("libraryName") val libraryName: String? = null,
    @SerialName("format") val format: Int = 2,
    @SerialName("primaryColor") val primaryColor: String? = null,
    @SerialName("secondaryColor") val secondaryColor: String? = null,
    @SerialName("latestReadDate") val latestReadDate: String? = null,
    @SerialName("minHoursToRead") val minHoursToRead: Int = 0,
    @SerialName("maxHoursToRead") val maxHoursToRead: Int = 0,
)

@Serializable
data class GroupedSeriesDto(
    @SerialName("seriesId") val seriesId: Int = 0,
    @SerialName("seriesName") val seriesName: String = "",
    @SerialName("localizedSeriesName") val localizedSeriesName: String? = null,
    @SerialName("libraryId") val libraryId: Int = 0,
    @SerialName("libraryType") val libraryType: Int = 0,
    @SerialName("chapterId") val chapterId: Int = 0,
    @SerialName("volumeId") val volumeId: Int = 0,
    @SerialName("created") val created: String? = null,
    @SerialName("format") val format: Int = 2,
    @SerialName("count") val count: Int = 0,
)

@Serializable
data class ProgressDto(
    @SerialName("volumeId") val volumeId: Int = 0,
    @SerialName("chapterId") val chapterId: Int = 0,
    @SerialName("pageNum") val pageNum: Int = 0,
    @SerialName("seriesId") val seriesId: Int = 0,
    @SerialName("libraryId") val libraryId: Int = 0,
    @SerialName("bookScrollId") val bookScrollId: String? = null,
    @SerialName("lastModifiedUtc") val lastModifiedUtc: String? = null,
)

@Serializable
data class DashboardStreamDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("isProvided") val isProvided: Boolean = false,
    @SerialName("order") val order: Int = 0,
    @SerialName("smartFilterEncoded") val smartFilterEncoded: String? = null,
    @SerialName("smartFilterId") val smartFilterId: Int? = null,
    @SerialName("streamType") val streamType: Int = 0,
    @SerialName("visible") val visible: Boolean = true,
    @SerialName("entityType") val entityType: Int = 0,
)

/** Filter body. Kavita requires a body even when empty (AGENTS.md §4.4). */
@Serializable
data class SeriesFilterV2Dto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String? = null,
    @SerialName("statements") val statements: List<SeriesFilterStatementDto> = emptyList(),
    @SerialName("combination") val combination: Int = 1,
    @SerialName("sortOptions") val sortOptions: SeriesSortOptionDto? = null,
    @SerialName("limitTo") val limitTo: Int = 0,
)

@Serializable
data class SeriesFilterStatementDto(
    @SerialName("field") val field: Int,
    @SerialName("comparison") val comparison: Int,
    @SerialName("value") val value: String,
)

@Serializable
data class SeriesSortOptionDto(
    @SerialName("sortField") val sortField: Int = 1,
    @SerialName("isAscending") val isAscending: Boolean = true,
)
