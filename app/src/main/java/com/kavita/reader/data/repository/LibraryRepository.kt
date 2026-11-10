package com.kavita.reader.data.repository

import com.kavita.reader.core.image.CoverUrlBuilder
import com.kavita.reader.core.network.ApiFactory
import com.kavita.reader.core.network.ErrorMapper
import com.kavita.reader.core.network.KavitaError
import com.kavita.reader.core.network.KavitaFailure
import com.kavita.reader.core.network.Outcome
import com.kavita.reader.data.mapper.ContentMapper
import com.kavita.reader.domain.model.Bookmark
import com.kavita.reader.domain.model.Chapter
import com.kavita.reader.domain.model.ContentFormat
import com.kavita.reader.domain.model.ReaderSession
import com.kavita.reader.domain.model.SeriesDetail
import com.kavita.reader.domain.model.SeriesFilter
import com.kavita.reader.domain.model.SeriesSummary
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A page of series plus the server-reported grand total.
 *
 * The total comes from the `Pagination` response header and is the only truthful count available:
 * `LibraryDto` has no series-count field, and `size` of a page is just that page. Using page size
 * as a count made every folder tile read "4 series" (AGENTS.md §4.3).
 */
data class SeriesPage(
    val items: List<SeriesSummary>,
    val totalItems: Int,
) {
    /** Falls back to the page size when the header was missing. */
    val totalOrPageSize: Int get() = if (totalItems > 0) totalItems else items.size
}

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
    private val seriesCache: SeriesListCache,
    private val activeServerStore: com.kavita.reader.core.auth.ActiveServerStore,
) {

    /**
     * Series in one library, paged and filtered, including the true number of series in scope.
     *
     * The library restriction is always injected as the first statement
     * (`SeriesFilterField.Libraries = 19`) so a user filter can never escape the library scope.
     * `WantToRead(26)` is only honoured by the server as a *top-level* pre-pass
     * (`SeriesRepository.ApplyWantToReadFilter`), never inside the And/Or group, so it is sent in
     * the same flat list — the server splits it out itself.
     *
     * @param forceRefresh bypass the in-memory cache (used by pull-to-refresh / after mutations)
     */
    suspend fun seriesPageInLibrary(
        libraryId: Int,
        page: Int = 1,
        pageSize: Int = 40,
        filter: SeriesFilter = SeriesFilter.Default,
        forceRefresh: Boolean = false,
    ): Outcome<SeriesPage> {
        val cacheKey = seriesCache.key(
            activeServerStore.activeId,
            "lib", libraryId, page, pageSize,
            filter.sort.apiValue,
            filter.ascending,
            filter.statements.joinToString(";") { "${it.field.apiValue}:${it.comparison.apiValue}:${it.value}" },
        )

        if (!forceRefresh) {
            seriesCache.getPage(cacheKey)?.let { return Outcome.Success(it) }
        }

        return try {
            val body = filter.toLibraryScopedDto(libraryId)
            val response = apiFactory.series().seriesForLibrary(
                pageNumber = page,
                pageSize = pageSize,
                filter = body,
            )
            // Retrofit does not throw on non-2xx when a Response<T> is requested.
            val dtos = response.body().orEmpty()
            if (!response.isSuccessful) {
                throw retrofit2.HttpException(response)
            }
            val result = SeriesPage(
                items = dtos.map(ContentMapper::series),
                totalItems = totalFrom(response.headers()["Pagination"]),
            )
            seriesCache.putPage(cacheKey, result)
            Outcome.Success(result)
        } catch (t: Throwable) {
            Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
        }
    }

    /**
     * Extracts `totalItems` from Kavita's `Pagination` response header.
     *
     * The header looks like `{"currentPage":1,"itemsPerPage":4,"totalItems":85,"totalPages":22}`.
     * A missing or malformed header yields 0, which callers must treat as "unknown" rather than
     * as a real count of zero.
     */
    private fun totalFrom(header: String?): Int {
        if (header.isNullOrBlank()) return 0
        return runCatching {
            val obj = kotlinx.serialization.json.Json
                .parseToJsonElement(header) as kotlinx.serialization.json.JsonObject
            obj["totalItems"]
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.content
                ?.toIntOrNull()
                ?: 0
        }.getOrDefault(0)
    }

    /** Convenience wrapper for callers that only need the page items (grids). */
    suspend fun seriesInLibrary(
        libraryId: Int,
        page: Int = 1,
        pageSize: Int = 40,
        filter: SeriesFilter = SeriesFilter.Default,
        forceRefresh: Boolean = false,
    ): Outcome<List<SeriesSummary>> = when (
        val outcome = seriesPageInLibrary(libraryId, page, pageSize, filter, forceRefresh)
    ) {
        is Outcome.Success -> Outcome.Success(outcome.value.items)
        is Outcome.Failure -> outcome
    }

    /** All series across libraries, honouring the same filter model (used by smart views). */
    suspend fun allSeries(
        page: Int = 1,
        pageSize: Int = 40,
        filter: SeriesFilter = SeriesFilter.Default,
    ): Outcome<List<SeriesSummary>> = try {
        val dtos = apiFactory.series().allSeries(
            pageNumber = page,
            pageSize = pageSize,
            context = 1,
            filter = filter.toDto(),
        )
        Outcome.Success(dtos.map(ContentMapper::series))
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    suspend fun seriesDetail(seriesId: Int): Outcome<SeriesDetail> = try {
        val series = apiFactory.series().series(seriesId)
        val volumes = apiFactory.seriesDetail().volumes(seriesId)
        Outcome.Success(ContentMapper.seriesDetail(series, volumes))
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    suspend fun hasProgress(seriesId: Int): Boolean =
        runCatching { apiFactory.seriesDetail().hasProgress(seriesId) }.getOrDefault(false)

    /** The chapter to resume from (first chapter when nothing was read yet). */
    suspend fun continuePoint(seriesId: Int): Outcome<Chapter> = try {
        Outcome.Success(ContentMapper.chapter(apiFactory.seriesDetail().continuePoint(seriesId)))
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    /**
     * Validation errors from ASP.NET are surfaced with field names, so the UI can say
     * "seriesId: The value must be greater than 0" instead of a generic 400.
     */
    suspend fun debugSeriesProbe(seriesId: Int): String = buildString {
        append("seriesId=").append(seriesId).append("\n")
        append("kavita=").append(
            runCatching { apiFactory.server().serverInfo().kavitaVersion }.getOrElse { "n/a" },
        ).append("\n")
        append("series=")
        append(
            runCatching { apiFactory.series().series(seriesId).name }
                .getOrElse { t -> "ERR " + ErrorMapper.from(t).message },
        )
    }

    suspend fun chapter(chapterId: Int): Outcome<Chapter> = try {
        Outcome.Success(ContentMapper.chapter(apiFactory.seriesDetail().chapter(chapterId)))
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
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
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    suspend fun markChapterRead(seriesId: Int, chapterId: Int): Outcome<Unit> = try {
        apiFactory.reader().markChapterRead(
            com.kavita.reader.data.remote.api.MarkChapterReadRequest(seriesId, chapterId),
        )
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    suspend fun markChapterUnread(seriesId: Int, chapterId: Int): Outcome<Unit> = try {
        // Kavita has no dedicated unread endpoint in the same shape; progress 0 clears it.
        apiFactory.reader().saveProgress(
            com.kavita.reader.data.remote.dto.SaveProgressRequest(
                libraryId = 0,
                seriesId = seriesId,
                volumeId = 0,
                chapterId = chapterId,
                pageNum = 0,
            ),
        )
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    suspend fun markVolumeRead(seriesId: Int, volumeId: Int): Outcome<Unit> = try {
        apiFactory.reader().markVolumeRead(
            com.kavita.reader.data.remote.api.MarkVolumeReadRequest(seriesId, volumeId),
        )
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    suspend fun markSeriesRead(seriesId: Int): Outcome<Unit> = try {
        apiFactory.reader().markSeriesRead(
            com.kavita.reader.data.remote.api.MarkReadRequest(seriesId),
        )
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    suspend fun bookmarks(chapterId: Int): List<Int> =
        runCatching { apiFactory.reader().chapterBookmarks(chapterId).map { it.page } }
            .getOrDefault(emptyList())

    /**
     * Every bookmark of the user, for the "My bookmarks" screen.
     *
     * Kavita ignores paging here and returns the whole list, so the screen does its own
     * presentation. Only a series-name filter is supported server-side; the body must still be a
     * `SeriesFilterV2Dto` because the server enumerates `Statements` unconditionally.
     */
    suspend fun allBookmarks(nameQuery: String = ""): Outcome<List<Bookmark>> = try {
        val body = SeriesFilter.Default
            .withNameQuery(nameQuery)
            .toDto()
        Outcome.Success(
            apiFactory.reader().allBookmarks(body).map(ContentMapper::bookmark),
        )
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    fun bookmarkPageUrl(chapterId: Int, pageNum: Int, imageOffset: Int = 0): String? =
        coverUrlBuilder.bookmarkPage(chapterId, pageNum, imageOffset)

    suspend fun addBookmark(chapterId: Int, seriesId: Int, volumeId: Int, page: Int): Outcome<Unit> = try {
        apiFactory.reader().addBookmark(
            com.kavita.reader.data.remote.dto.BookmarkDto(
                page = page,
                chapterId = chapterId,
                seriesId = seriesId,
                volumeId = volumeId,
            ),
        )
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    suspend fun removeBookmark(chapterId: Int, seriesId: Int, volumeId: Int, page: Int): Outcome<Unit> = try {
        apiFactory.reader().removeBookmark(
            com.kavita.reader.data.remote.dto.BookmarkDto(
                page = page,
                chapterId = chapterId,
                seriesId = seriesId,
                volumeId = volumeId,
            ),
        )
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    fun coverUrl(seriesId: Int): String? = coverUrlBuilder.series(seriesId)

    fun readerPageUrl(chapterId: Int, pageIndex: Int): String? =
        coverUrlBuilder.readerPage(chapterId, pageIndex)

    /** Formats that need a non-manga reader are gated in M3; M1 only renders image content. */
    fun supportsImageReader(format: ContentFormat): Boolean =
        format == ContentFormat.Image || format == ContentFormat.Archive || format == ContentFormat.Unknown
}
