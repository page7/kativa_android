package com.kavita.reader.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Search results are grouped by entity type (Kavita `SearchResultGroupDto`). */
@Serializable
data class SearchResultGroupDto(
    @SerialName("libraries") val libraries: List<SearchResultDto> = emptyList(),
    @SerialName("series") val series: List<SearchResultDto> = emptyList(),
    @SerialName("collections") val collections: List<SearchResultDto> = emptyList(),
    @SerialName("readingLists") val readingLists: List<SearchResultDto> = emptyList(),
    @SerialName("persons") val persons: List<SearchResultDto> = emptyList(),
    @SerialName("genres") val genres: List<SearchResultDto> = emptyList(),
    @SerialName("tags") val tags: List<SearchResultDto> = emptyList(),
    @SerialName("files") val files: List<SearchResultDto> = emptyList(),
    @SerialName("chapters") val chapters: List<SearchResultDto> = emptyList(),
    @SerialName("bookmarks") val bookmarks: List<SearchResultDto> = emptyList(),
    @SerialName("annotations") val annotations: List<SearchResultDto> = emptyList(),
)

@Serializable
data class SearchResultDto(
    @SerialName("seriesId") val seriesId: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("originalName") val originalName: String? = null,
    @SerialName("sortName") val sortName: String? = null,
    @SerialName("localizedName") val localizedName: String? = null,
    @SerialName("format") val format: Int = 2,
    @SerialName("libraryName") val libraryName: String? = null,
    @SerialName("libraryId") val libraryId: Int = 0,
    @SerialName("releaseYear") val releaseYear: Int? = null,
    @SerialName("volumeCount") val volumeCount: Int = 0,
    @SerialName("chapterCount") val chapterCount: Int = 0,
)

/** Person / genre / tag rows used by the browse screen (M2 discovery). */
@Serializable
data class PersonDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("aliases") val aliases: List<String> = emptyList(),
    @SerialName("coverImage") val coverImage: String? = null,
)

@Serializable
data class GenreTagDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("title") val title: String = "",
)

@Serializable
data class TagDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("title") val title: String = "",
)

/** Collection / reading-list rows surfaced in search results and discovery. */
@Serializable
data class CollectionDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("title") val title: String = "",
    @SerialName("summary") val summary: String? = null,
    @SerialName("promoted") val promoted: Boolean = false,
    @SerialName("coverImage") val coverImage: String? = null,
    @SerialName("itemCount") val itemCount: Int = 0,
    @SerialName("owner") val owner: String? = null,
    @SerialName("primaryColor") val primaryColor: String? = null,
    @SerialName("secondaryColor") val secondaryColor: String? = null,
)

@Serializable
data class ReadingListSummaryDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("title") val title: String = "",
    @SerialName("summary") val summary: String? = null,
    @SerialName("promoted") val promoted: Boolean = false,
    @SerialName("coverImage") val coverImage: String? = null,
    @SerialName("itemCount") val itemCount: Int = 0,
    @SerialName("startingYear") val startingYear: Int = 0,
    @SerialName("endingYear") val endingYear: Int = 0,
    @SerialName("ownerUserName") val ownerUserName: String? = null,
    @SerialName("primaryColor") val primaryColor: String? = null,
    @SerialName("secondaryColor") val secondaryColor: String? = null,
)
