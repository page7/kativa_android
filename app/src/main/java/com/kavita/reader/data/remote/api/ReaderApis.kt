package com.kavita.reader.data.remote.api

import com.kavita.reader.data.remote.dto.BookmarkDto
import com.kavita.reader.data.remote.dto.ChapterDto
import com.kavita.reader.data.remote.dto.ChapterInfoDto
import com.kavita.reader.data.remote.dto.HourEstimateRangeDto
import com.kavita.reader.data.remote.dto.ProgressDto
import com.kavita.reader.data.remote.dto.SaveProgressRequest
import com.kavita.reader.data.remote.dto.VolumeDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
/**
 * M1 reader surface: chapter info, progress, bookmarks and chapter transitions.
 *
 * `pageNum` in progress is 1-based; `page` in image URLs is 0-based. The conversion lives in
 * exactly one place: `ReaderProgressMapper` (AGENTS.md §4.6 item 1).
 */
interface ReaderApi {

    /** Side effect: warms the server-side page cache for this chapter (§4.6 item 3). */
    @GET("Reader/chapter-info")
    suspend fun chapterInfo(
        @Query("chapterId") chapterId: Int,
        @Query("includeDimensions") includeDimensions: Boolean = true,
        @Query("extractPdf") extractPdf: Boolean = false,
    ): ChapterInfoDto

    /** No progress returns `pageNum = 0` and all ids zeroed — ids must come from chapter-info. */
    @GET("Reader/get-progress")
    suspend fun progress(@Query("chapterId") chapterId: Int): ProgressDto

    @POST("Reader/progress")
    suspend fun saveProgress(@Body body: SaveProgressRequest)

    @GET("Reader/next-chapter")
    suspend fun nextChapter(
        @Query("seriesId") seriesId: Int,
        @Query("volumeId") volumeId: Int,
        @Query("currentChapterId") currentChapterId: Int,
    ): Int

    @GET("Reader/prev-chapter")
    suspend fun prevChapter(
        @Query("seriesId") seriesId: Int,
        @Query("volumeId") volumeId: Int,
        @Query("currentChapterId") currentChapterId: Int,
    ): Int

    @GET("Reader/chapter-bookmarks")
    suspend fun chapterBookmarks(@Query("chapterId") chapterId: Int): List<BookmarkDto>

    /**
     * Every bookmark of the signed-in user.
     *
     * The body must be a `SeriesFilterV2Dto` — Kavita dereferences `Statements` without a null
     * check, so an empty body is a 500. Only `SeriesName` statements are honoured (plus the sort
     * options); paging is ignored and the whole list comes back (docs/API.md §3).
     */
    @POST("Reader/all-bookmarks")
    suspend fun allBookmarks(
        @Body filter: com.kavita.reader.data.remote.dto.SeriesFilterV2Dto,
    ): List<BookmarkDto>

    @POST("Reader/bookmark")
    suspend fun addBookmark(@Body body: BookmarkDto)

    @POST("Reader/unbookmark")
    suspend fun removeBookmark(@Body body: BookmarkDto)

    @POST("Reader/mark-chapter-read")
    suspend fun markChapterRead(@Body body: MarkChapterReadRequest)

    @POST("Reader/mark-volume-read")
    suspend fun markVolumeRead(@Body body: MarkVolumeReadRequest)

    @POST("Reader/mark-read")
    suspend fun markSeriesRead(@Body body: MarkReadRequest)
}

/** Series detail endpoints — stable ones only (AGENTS.md §4.6 item 5). */
interface SeriesDetailApi {

    @GET("Series/volumes")
    suspend fun volumes(@Query("seriesId") seriesId: Int): List<VolumeDto>

    @GET("Series/volume")
    suspend fun volume(@Query("volumeId") volumeId: Int): VolumeDto

    @GET("Series/chapter")
    suspend fun chapter(@Query("chapterId") chapterId: Int): ChapterDto

    @GET("Reader/has-progress")
    suspend fun hasProgress(@Query("seriesId") seriesId: Int): Boolean

    /** Chapter to resume from; the first chapter when there is no progress. */
    @GET("Reader/continue-point")
    suspend fun continuePoint(@Query("seriesId") seriesId: Int): ChapterDto

    @GET("Reader/time-left")
    suspend fun timeLeft(@Query("seriesId") seriesId: Int): HourEstimateRangeDto

    @GET("Reader/time-left-for-chapter")
    suspend fun timeLeftForChapter(
        @Query("seriesId") seriesId: Int,
        @Query("chapterId") chapterId: Int,
    ): HourEstimateRangeDto
}

@Serializable
data class MarkChapterReadRequest(
    @SerialName("seriesId") val seriesId: Int,
    @SerialName("chapterId") val chapterId: Int,
    /** Manual mark-read keeps this false so reading-session stats are not fabricated. */
    @SerialName("generateReadingSession") val generateReadingSession: Boolean = false,
)

@Serializable
data class MarkVolumeReadRequest(
    @SerialName("seriesId") val seriesId: Int,
    @SerialName("volumeId") val volumeId: Int,
    @SerialName("generateReadingSession") val generateReadingSession: Boolean = false,
)

@Serializable
data class MarkReadRequest(
    @SerialName("seriesId") val seriesId: Int,
    @SerialName("generateReadingSession") val generateReadingSession: Boolean = false,
)
