package com.kavita.reader.data.remote.api

import com.kavita.reader.data.remote.dto.CollectionDto
import com.kavita.reader.data.remote.dto.GenreTagDto
import com.kavita.reader.data.remote.dto.PersonDto
import com.kavita.reader.data.remote.dto.ReadingListSummaryDto
import com.kavita.reader.data.remote.dto.SearchResultGroupDto
import com.kavita.reader.data.remote.dto.TagDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Search and discovery endpoints.
 *
 * `includeChapterAndFiles=false` is deliberate: chapter/file hits are noise on mobile and the
 * response gets much larger (docs/API.md §6).
 */
interface SearchApi {

    @GET("Search/search")
    suspend fun search(
        @Query("queryString") queryString: String,
        @Query("includeChapterAndFiles") includeChapterAndFiles: Boolean = false,
    ): SearchResultGroupDto

    @GET("Metadata/genres")
    suspend fun genres(@Query("libraryIds") libraryIds: String? = null): List<GenreTagDto>

    @GET("Metadata/tags")
    suspend fun tags(@Query("libraryIds") libraryIds: String? = null): List<TagDto>

    @GET("Person/search")
    suspend fun personSearch(@Query("queryString") queryString: String): List<PersonDto>
}

interface CollectionApi {

    @GET("Collection")
    suspend fun collections(
        @Query("ownedOnly") ownedOnly: Boolean = false,
        @Query("sortByLastModified") sortByLastModified: Boolean = false,
    ): List<CollectionDto>

    /** Series inside one collection. */
    @GET("Series/series-by-collection")
    suspend fun seriesInCollection(
        @Query("collectionId") collectionId: Int,
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 40,
    ): List<com.kavita.reader.data.remote.dto.SeriesDto>
}

interface WantToReadApi {

    /** Filtered, paged list of series on the user's Want To Read list. */
    @retrofit2.http.POST("want-to-read/v2")
    suspend fun series(
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 40,
        @Body filter: com.kavita.reader.data.remote.dto.SeriesFilterV2Dto =
            com.kavita.reader.data.remote.dto.SeriesFilterV2Dto(),
    ): List<com.kavita.reader.data.remote.dto.SeriesDto>

    /** Returns the plain text `true`/`false`, not JSON (docs/API.md §13.8). */
    @GET("want-to-read")
    suspend fun isInList(@Query("seriesId") seriesId: Int): String

    @retrofit2.http.POST("want-to-read/add-series")
    suspend fun addSeries(@Body body: UpdateWantToReadRequest)

    @retrofit2.http.POST("want-to-read/remove-series")
    suspend fun removeSeries(@Body body: UpdateWantToReadRequest)
}

@kotlinx.serialization.Serializable
data class UpdateWantToReadRequest(
    @kotlinx.serialization.SerialName("seriesIds") val seriesIds: List<Int>,
)

interface ReadingListApi {

    /**
     * `POST` with no body: the endpoint takes everything from query parameters.
     * Paging headers are ignored here — the list screen shows the first page only for now.
     */
    @retrofit2.http.POST("ReadingList/lists")
    suspend fun lists(
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
        @Query("includePromoted") includePromoted: Boolean = true,
        @Query("sortByLastModified") sortByLastModified: Boolean = false,
    ): List<ReadingListSummaryDto>
}
