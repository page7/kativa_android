package com.kativa.reader.domain.model

/** A chapter as the reader sees it — already ordered and progress-aware. */
data class Chapter(
    val id: Int,
    val title: String,
    val number: String,
    val range: String?,
    val volumeId: Int,
    val volumeTitle: String?,
    val pages: Int,
    val pagesRead: Int,
    val isSpecial: Boolean,
    val sortOrder: Float,
    val releaseDate: String?,
    val wordCount: Long,
    val minHoursToRead: Int,
    val maxHoursToRead: Int,
    /** Raw `minNumber`; carries Kavita's sentinel values (-100000 / 100000). */
    val minNumber: Float = 0f,
    val maxNumber: Float = 0f,
) {
    val isRead: Boolean get() = pages > 0 && pagesRead >= pages

    /** True when Kavita could not parse a chapter number for this file. */
    val isUnnumbered: Boolean get() = KavitaNumbering.isUnnumbered(minNumber)

    /**
     * The server pre-formats `ChapterDto.Title` (e.g. `"Chapter -100000"`), so a title is only
     * usable for display when it carries no sentinel token.
     */
    val safeTitle: String?
        get() = title.trim().takeIf {
            it.isNotBlank() && !KavitaNumbering.containsSentinel(it)
        }

    /** Regular chapter numbers only; never a sentinel. */
    val displayNumber: String?
        get() = when {
            isUnnumbered -> null
            range != null && range.isNotBlank() && !range.startsWith("-") -> range
            number.isNotBlank() && !number.startsWith("-") -> number
            else -> null
        }
}

data class Volume(
    val id: Int,
    val number: Int,
    val name: String,
    val pages: Int,
    val pagesRead: Int,
    val chapters: List<Chapter>,
    /** Raw `minNumber`; carries Kavita's sentinel values (-100000 / 100000). */
    val minNumber: Float = 0f,
    val maxNumber: Float = 0f,
) {
    val isRead: Boolean get() = pages > 0 && pagesRead >= pages

    val progress: Float
        get() = if (pages <= 0) 0f else (pagesRead.toFloat() / pages).coerceIn(0f, 1f)

    /** Loose-leaf volume: holds chapters whose own file had no volume (or chapter) number. */
    val isLooseLeaf: Boolean get() = KavitaNumbering.isUnnumbered(minNumber)

    /** Synthetic volume that only exists to group specials. */
    val isSpecials: Boolean get() = KavitaNumbering.isSpecialsVolume(minNumber)

    /** Regular volumes are numbered; loose-leaf/specials are rendered as their own sections. */
    val isRegular: Boolean get() = !KavitaNumbering.isSyntheticVolume(minNumber)

    /** `VolumeDto.Name` may also arrive pre-formatted with a sentinel; never display that. */
    val safeName: String?
        get() = name.trim().takeIf {
            it.isNotBlank() && !it.startsWith("-") && !KavitaNumbering.containsSentinel(it)
        }
}

/** Series detail aggregate. */
data class SeriesDetail(
    val seriesId: Int,
    val name: String,
    val sortName: String,
    val libraryId: Int,
    val libraryName: String,
    val format: ContentFormat,
    val pages: Int,
    val pagesRead: Int,
    val volumes: List<Volume>,
    val publicationStatus: PublicationStatus = PublicationStatus.Unknown,
    val releaseYear: Int? = null,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
) {
    val chapters: List<Chapter> get() = volumes.flatMap { it.chapters }.sortedBy { it.sortOrder }

    /** Numbered volumes, in reading order. The synthetic loose-leaf/specials volumes are excluded. */
    val regularVolumes: List<Volume> get() = volumes.filter { it.isRegular }.sortedBy { it.minNumber }

    /** Chapters that belong to the loose-leaf volume ("no volume number parsed"). */
    val looseLeafChapters: List<Chapter>
        get() = volumes.filter { it.isLooseLeaf }.flatMap { it.chapters }.sortedBy { it.sortOrder }

    /** Specials, from both `isSpecial` flags and the synthetic specials volume. */
    val specialChapters: List<Chapter>
        get() = volumes.filter { it.isSpecials }.flatMap { it.chapters }
            .ifEmpty { chapters.filter { it.isSpecial } }
            .sortedBy { it.sortOrder }

    val totalChapters: Int get() = chapters.size

    val readChapters: Int get() = chapters.count { it.isRead }

    val progress: Float
        get() = if (pages <= 0) 0f else (pagesRead.toFloat() / pages).coerceIn(0f, 1f)

    val isComplete: Boolean get() = totalChapters > 0 && readChapters == totalChapters
}

/** Progress for one chapter, already converted to 1-based display semantics. */
data class ChapterProgress(
    val chapterId: Int,
    val pageNum: Int,
    val bookScrollId: String?,
    val lastModifiedUtc: String? = null,
) {
    val hasProgress: Boolean get() = pageNum > 0
}

/** What the reader needs before it can render a single page. */
data class ReaderSession(
    val chapterId: Int,
    val seriesId: Int,
    val volumeId: Int,
    val libraryId: Int,
    val seriesName: String,
    val chapterTitle: String,
    val chapterNumber: String,
    val volumeNumber: String,
    val format: ContentFormat,
    val totalPages: Int,
    val startPageNum: Int,
    val pageDimensions: List<PageDimension> = emptyList(),
) {
    /** 0-based index used by `Reader/image?page=`. */
    val startPageIndex: Int get() = (startPageNum - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0))

    /**
     * Page image URL. Built here (not in the composable) so the API key and host are resolved
     * once per session. Null when the session is not signed in.
     */
    fun pageUrl(pageIndex: Int, baseUrl: String?, imageKey: String?): String? {
        if (baseUrl.isNullOrBlank() || imageKey.isNullOrBlank()) return null
        val root = baseUrl.trimEnd('/')
        val key = java.net.URLEncoder.encode(imageKey, "UTF-8")
        return "$root/Reader/image?chapterId=$chapterId&page=$pageIndex&apiKey=$key"
    }
}

data class PageDimension(
    val width: Int,
    val height: Int,
    val pageNumber: Int,
    val isWide: Boolean,
)
