package com.kativa.reader.data.repository

import com.kativa.reader.core.image.CoverUrlBuilder
import com.kativa.reader.core.network.ApiFactory
import com.kativa.reader.core.network.ErrorMapper
import com.kativa.reader.core.network.KativaError
import com.kativa.reader.core.network.KativaFailure
import com.kativa.reader.core.network.Outcome
import com.kativa.reader.data.mapper.ContentMapper
import com.kativa.reader.data.remote.dto.SeriesFilterV2Dto
import com.kativa.reader.data.remote.dto.SeriesFilterStatementDto
import com.kativa.reader.domain.model.Chapter
import com.kativa.reader.domain.model.ContentFormat
import com.kativa.reader.domain.model.ReaderSession
import com.kativa.reader.domain.model.SeriesDetail
import com.kativa.reader.domain.model.SeriesSummary
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M1 content reads: library grid, series detail, reader bootstrap.
 *
 * Every call returns [Outcome] so screens never see raw exceptions
 * (AGENTS.md §3 rule 4 / §4.5).
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val apiFactory: ApiFactory,
    private val coverUrlBuilder: CoverUrlBuilder,
) {

    /** Series in one library, paged. Library filter uses `SeriesFilterField.Libraries = 19`. */
    suspend fun seriesInLibrary(
        libraryId: Int,
        page: Int = 1,
        pageSize: Int = 40,
        sortField: Int = 1,
        ascending: Boolean = true,
    ): Outcome<List<SeriesSummary>> = try {
        val filter = SeriesFilterV2Dto(
            statements = listOf(
                SeriesFilterStatementDto(field = 19, comparison = 0, value = libraryId.toString()),
            ),
            combination = 1,
            sortOptions = com.kativa.reader.data.remote.dto.SeriesSortOptionDto(sortField, ascending),
        )
        val dtos = apiFactory.series().seriesForLibrary(
            pageNumber = page,
            pageSize = pageSize,
            filter = filter,
        )
        Outcome.Success(dtos.map(ContentMapper::series))
    } catch (t: Throwable) {
        Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
    }

    suspend fun seriesDetail(seriesId: Int): Outcome<SeriesDetail> = try {
        val series = apiFactory.series().series(seriesId)
        val volumes = apiFactory.seriesDetail().volumes(seriesId)
        Outcome.Success(ContentMapper.seriesDetail(series, volumes))
    } catch (t: Throwable) {
        Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
    }

    suspend fun hasProgress(seriesId: Int): Boolean =
        runCatching { apiFactory.seriesDetail().hasProgress(seriesId) }.getOrDefault(false)

    /** The chapter to resume from (first chapter when nothing was read yet). */
    suspend fun continuePoint(seriesId: Int): Outcome<Chapter> = try {
        Outcome.Success(ContentMapper.chapter(apiFactory.seriesDetail().continuePoint(seriesId)))
    } catch (t: Throwable) {
        Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
    }

    suspend fun chapter(chapterId: Int): Outcome<Chapter> = try {
        Outcome.Success(ContentMapper.chapter(apiFactory.seriesDetail().chapter(chapterId)))
    } catch (t: Throwable) {
        Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
    }

    suspend fun nextChapter(seriesId: Int, volumeId: Int, currentChapterId: Int): Int =
        runCatching { apiFactory.reader().nextChapter(seriesId, volumeId, currentChapterId) }
            .getOrDefault(0)

    suspend fun prevChapter(seriesId: Int, volumeId: Int, currentChapterId: Int): Int =
        runCatching { apiFactory.reader().prevChapter(seriesId, volumeId, currentChapterId) }
            .getOrDefault(0)

    /**
     * Prepares a reader session: chapter-info (also warms the server cache), then progress.
     * Progress ids are deliberately ignored — chapter-info is the source of truth
     * (AGENTS.md §4.6 item 7).
     */
    suspend fun openChapter(chapterId: Int): Outcome<ReaderSession> = try {
        val info = apiFactory.reader().chapterInfo(chapterId = chapterId, includeDimensions = true)
        val progress = runCatching { apiFactory.reader().progress(chapterId) }.getOrNull()
        val startPage = progress?.let { ContentMapper.resumePageNum(it, info.pages) } ?: 1
        Outcome.Success(ContentMapper.readerSession(chapterId, info, startPage))
    } catch (t: Throwable) {
        Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
    }

    suspend fun markChapterRead(seriesId: Int, chapterId: Int): Outcome<Unit> = try {
        apiFactory.reader().markChapterRead(
            com.kativa.reader.data.remote.api.MarkChapterReadRequest(seriesId, chapterId),
        )
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
    }

    suspend fun markChapterUnread(seriesId: Int, chapterId: Int): Outcome<Unit> = try {
        // Kavita has no dedicated unread endpoint in the same shape; progress 0 clears it.
        apiFactory.reader().saveProgress(
            com.kativa.reader.data.remote.dto.SaveProgressRequest(
                libraryId = 0,
                seriesId = seriesId,
                volumeId = 0,
                chapterId = chapterId,
                pageNum = 0,
            ),
        )
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
    }

    suspend fun markVolumeRead(seriesId: Int, volumeId: Int): Outcome<Unit> = try {
        apiFactory.reader().markVolumeRead(
            com.kativa.reader.data.remote.api.MarkVolumeReadRequest(seriesId, volumeId),
        )
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
    }

    suspend fun markSeriesRead(seriesId: Int): Outcome<Unit> = try {
        apiFactory.reader().markSeriesRead(
            com.kativa.reader.data.remote.api.MarkReadRequest(seriesId),
        )
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
    }

    suspend fun bookmarks(chapterId: Int): List<Int> =
        runCatching { apiFactory.reader().chapterBookmarks(chapterId).map { it.page } }
            .getOrDefault(emptyList())

    suspend fun addBookmark(chapterId: Int, seriesId: Int, volumeId: Int, page: Int): Outcome<Unit> = try {
        apiFactory.reader().addBookmark(
            com.kativa.reader.data.remote.dto.BookmarkDto(
                page = page,
                chapterId = chapterId,
                seriesId = seriesId,
                volumeId = volumeId,
            ),
        )
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
    }

    suspend fun removeBookmark(chapterId: Int, seriesId: Int, volumeId: Int, page: Int): Outcome<Unit> = try {
        apiFactory.reader().removeBookmark(
            com.kativa.reader.data.remote.dto.BookmarkDto(
                page = page,
                chapterId = chapterId,
                seriesId = seriesId,
                volumeId = volumeId,
            ),
        )
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
    }

    fun coverUrl(seriesId: Int): String? = coverUrlBuilder.series(seriesId)

    fun readerPageUrl(chapterId: Int, pageIndex: Int): String? =
        coverUrlBuilder.readerPage(chapterId, pageIndex)

    /** Formats that need a non-manga reader are gated in M3; M1 only renders image content. */
    fun supportsImageReader(format: ContentFormat): Boolean =
        format == ContentFormat.Image || format == ContentFormat.Archive || format == ContentFormat.Unknown
}
