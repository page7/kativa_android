package com.kativa.reader.data.mapper

import com.kativa.reader.data.remote.dto.ChapterDto
import com.kativa.reader.data.remote.dto.ChapterInfoDto
import com.kativa.reader.data.remote.dto.PageDimensionDto
import com.kativa.reader.data.remote.dto.ProgressDto
import com.kativa.reader.data.remote.dto.SeriesDto
import com.kativa.reader.data.remote.dto.VolumeDto
import com.kativa.reader.domain.model.Chapter
import com.kativa.reader.domain.model.ChapterProgress
import com.kativa.reader.domain.model.ContentFormat
import com.kativa.reader.domain.model.PageDimension
import com.kativa.reader.domain.model.ReaderSession
import com.kativa.reader.domain.model.SeriesDetail
import com.kativa.reader.domain.model.SeriesSummary
import com.kativa.reader.domain.model.Volume

/**
 * DTO → domain mapping. This is the only layer allowed to know DTO field names
 * (AGENTS.md §3 rule 1).
 */
object ContentMapper {

    fun series(dto: SeriesDto): SeriesSummary = SeriesSummary(
        id = dto.id,
        name = dto.localizedName?.takeIf { it.isNotBlank() } ?: dto.name,
        libraryId = dto.libraryId,
        libraryName = dto.libraryName.orEmpty(),
        format = ContentFormat.from(dto.format),
        pages = dto.pages,
        pagesRead = dto.pagesRead,
        primaryColor = dto.primaryColor,
        secondaryColor = dto.secondaryColor,
    )

    fun chapter(dto: ChapterDto): Chapter = Chapter(
        id = dto.id,
        title = dto.title.ifBlank { dto.titleName },
        number = dto.number,
        range = dto.range,
        volumeId = dto.volumeId,
        volumeTitle = dto.volumeTitle,
        pages = dto.pages,
        pagesRead = dto.pagesRead,
        isSpecial = dto.isSpecial,
        sortOrder = dto.sortOrder,
        releaseDate = dto.releaseDate,
        wordCount = dto.wordCount,
        minHoursToRead = dto.minHoursToRead,
        maxHoursToRead = dto.maxHoursToRead,
    )

    fun volume(dto: VolumeDto): Volume = Volume(
        id = dto.id,
        number = dto.number,
        name = dto.name,
        pages = dto.pages,
        pagesRead = dto.pagesRead,
        // sortOrder is authoritative; never sort by the string `number` (AGENTS.md §4.6).
        chapters = dto.chapters.map(::chapter).sortedBy { it.sortOrder },
    )

    fun seriesDetail(dto: SeriesDto, volumes: List<VolumeDto>): SeriesDetail = SeriesDetail(
        seriesId = dto.id,
        name = dto.localizedName?.takeIf { it.isNotBlank() } ?: dto.name,
        sortName = dto.sortName ?: dto.name,
        libraryId = dto.libraryId,
        libraryName = dto.libraryName.orEmpty(),
        format = ContentFormat.from(dto.format),
        pages = dto.pages,
        pagesRead = dto.pagesRead,
        volumes = volumes.map(::volume).filter { it.chapters.isNotEmpty() || it.pages > 0 },
        primaryColor = dto.primaryColor,
        secondaryColor = dto.secondaryColor,
    )

    /**
     * Progress conversion lives here and nowhere else (AGENTS.md §4.6 item 1/2):
     *  - `pageNum == 0`        → no progress, start at page 1
     *  - `0 < pageNum < pages` → resume on that page
     *  - `pageNum == pages`    → last page shown, not finished; resume on it
     *  - `pageNum >= pages + 1`→ finished; a (re)read starts from page 1
     */
    fun resumePageNum(progress: ProgressDto, totalPages: Int): Int {
        if (totalPages <= 0) return 1
        val raw = progress.pageNum
        return when {
            raw <= 0 -> 1
            raw > totalPages -> 1
            else -> raw
        }
    }

    fun chapterProgress(dto: ProgressDto): ChapterProgress = ChapterProgress(
        chapterId = dto.chapterId,
        pageNum = dto.pageNum,
        bookScrollId = dto.bookScrollId,
        lastModifiedUtc = dto.lastModifiedUtc,
    )

    fun pageDimension(dto: PageDimensionDto): PageDimension = PageDimension(
        width = dto.width,
        height = dto.height,
        pageNumber = dto.pageNumber,
        isWide = dto.isWide,
    )

    fun readerSession(chapterId: Int, info: ChapterInfoDto, startPageNum: Int): ReaderSession =
        ReaderSession(
            chapterId = chapterId,
            seriesId = info.seriesId,
            volumeId = info.volumeId,
            libraryId = info.libraryId,
            seriesName = info.seriesName,
            chapterTitle = info.title.ifBlank { info.chapterTitle },
            chapterNumber = info.chapterNumber,
            volumeNumber = info.volumeNumber,
            format = ContentFormat.from(info.seriesFormat),
            totalPages = info.pages,
            startPageNum = startPageNum,
            pageDimensions = info.pageDimensions.map(::pageDimension),
        )
}
