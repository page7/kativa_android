package com.kativa.reader.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Volume / chapter / reader wire models (Kavita v0.9.1).
 *
 * `ChapterDto.sortOrder` is authoritative for ordering — never sort by `title` or `number`
 * as a string (AGENTS.md §4.6).
 */
@Serializable
data class VolumeDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("number") val number: Int = 0,
    @SerialName("minNumber") val minNumber: Float = 0f,
    @SerialName("maxNumber") val maxNumber: Float = 0f,
    @SerialName("name") val name: String = "",
    @SerialName("pages") val pages: Int = 0,
    @SerialName("pagesRead") val pagesRead: Int = 0,
    @SerialName("seriesId") val seriesId: Int = 0,
    @SerialName("createdUtc") val createdUtc: String? = null,
    @SerialName("lastModifiedUtc") val lastModifiedUtc: String? = null,
    @SerialName("coverImage") val coverImage: String? = null,
    @SerialName("chapters") val chapters: List<ChapterDto> = emptyList(),
    @SerialName("minHoursToRead") val minHoursToRead: Int = 0,
    @SerialName("maxHoursToRead") val maxHoursToRead: Int = 0,
)

@Serializable
data class ChapterDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("number") val number: String = "0",
    @SerialName("range") val range: String? = null,
    @SerialName("minNumber") val minNumber: Float = 0f,
    @SerialName("maxNumber") val maxNumber: Float = 0f,
    @SerialName("sortOrder") val sortOrder: Float = 0f,
    @SerialName("pages") val pages: Int = 0,
    @SerialName("pagesRead") val pagesRead: Int = 0,
    @SerialName("isSpecial") val isSpecial: Boolean = false,
    @SerialName("title") val title: String = "",
    @SerialName("titleName") val titleName: String = "",
    @SerialName("volumeId") val volumeId: Int = 0,
    @SerialName("volumeTitle") val volumeTitle: String? = null,
    @SerialName("createdUtc") val createdUtc: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("summary") val summary: String? = null,
    @SerialName("wordCount") val wordCount: Long = 0,
    @SerialName("minHoursToRead") val minHoursToRead: Int = 0,
    @SerialName("maxHoursToRead") val maxHoursToRead: Int = 0,
)

/** Returned by `GET Reader/chapter-info`; opening it also warms the server-side page cache. */
@Serializable
data class ChapterInfoDto(
    @SerialName("chapterNumber") val chapterNumber: String = "",
    @SerialName("volumeNumber") val volumeNumber: String = "",
    @SerialName("volumeId") val volumeId: Int = 0,
    @SerialName("seriesName") val seriesName: String = "",
    @SerialName("seriesFormat") val seriesFormat: Int = 2,
    @SerialName("seriesId") val seriesId: Int = 0,
    @SerialName("libraryId") val libraryId: Int = 0,
    @SerialName("libraryType") val libraryType: Int = 0,
    @SerialName("chapterTitle") val chapterTitle: String = "",
    @SerialName("pages") val pages: Int = 0,
    @SerialName("fileName") val fileName: String = "",
    @SerialName("isSpecial") val isSpecial: Boolean = false,
    @SerialName("subtitle") val subtitle: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("seriesTotalPages") val seriesTotalPages: Int = 0,
    @SerialName("seriesTotalPagesRead") val seriesTotalPagesRead: Int = 0,
    @SerialName("pageDimensions") val pageDimensions: List<PageDimensionDto> = emptyList(),
    @SerialName("doublePairs") val doublePairs: Map<String, Int> = emptyMap(),
)

@Serializable
data class PageDimensionDto(
    @SerialName("width") val width: Int = 0,
    @SerialName("height") val height: Int = 0,
    @SerialName("pageNumber") val pageNumber: Int = 0,
    @SerialName("fileName") val fileName: String = "",
    @SerialName("isWide") val isWide: Boolean = false,
)

/**
 * `pageNum` is **1-based**; `maxPages + 1` means "chapter finished".
 * `bookScrollId` carries the EPUB scroll anchor.
 */
@Serializable
data class SaveProgressRequest(
    @SerialName("libraryId") val libraryId: Int,
    @SerialName("seriesId") val seriesId: Int,
    @SerialName("volumeId") val volumeId: Int,
    @SerialName("chapterId") val chapterId: Int,
    @SerialName("pageNum") val pageNum: Int,
    @SerialName("bookScrollId") val bookScrollId: String? = null,
)

@Serializable
data class BookmarkDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("page") val page: Int = 0,
    @SerialName("volumeId") val volumeId: Int = 0,
    @SerialName("seriesId") val seriesId: Int = 0,
    @SerialName("chapterId") val chapterId: Int = 0,
    @SerialName("imageOffset") val imageOffset: Int = 0,
    @SerialName("xPath") val xPath: String? = null,
    @SerialName("chapterTitle") val chapterTitle: String? = null,
    /** Only populated by `POST Reader/all-bookmarks` (the chapter/volume endpoints omit it). */
    @SerialName("series") val series: SeriesDto? = null,
)

@Serializable
data class HourEstimateRangeDto(
    @SerialName("minHours") val minHours: Int = 0,
    @SerialName("maxHours") val maxHours: Int = 0,
    @SerialName("avgHours") val avgHours: Double = 0.0,
    @SerialName("wordCount") val wordCount: Long = 0,
    @SerialName("pageCount") val pageCount: Int = 0,
)
