package com.kavita.reader.data.remote.api

import com.kavita.reader.data.remote.dto.CreateAuthKeyRequest
import com.kavita.reader.data.remote.dto.DashboardStreamDto
import com.kavita.reader.data.remote.dto.GroupedSeriesDto
import com.kavita.reader.data.remote.dto.LoginRequest
import com.kavita.reader.data.remote.dto.ProgressDto
import com.kavita.reader.data.remote.dto.SeriesDto
import com.kavita.reader.data.remote.dto.SeriesFilterV2Dto
import com.kavita.reader.data.remote.dto.ServerInfoSlimDto
import com.kavita.reader.data.remote.dto.TokenRequestDto
import com.kavita.reader.data.remote.dto.UserDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Auth + account endpoints. Paths never start with `/` because the Retrofit base URL
 * already ends in `api/` (AGENTS.md §4.1).
 */
interface AccountApi {

    @POST("Account/login")
    suspend fun login(@Body body: LoginRequest): UserDto

    @GET("Account")
    suspend fun currentUser(): UserDto

    @POST("Account/refresh-token")
    suspend fun refreshToken(@Body body: TokenRequestDto): TokenRequestDto

    @POST("Account/create-auth-key")
    suspend fun createAuthKey(@Body body: CreateAuthKeyRequest): com.kavita.reader.data.remote.dto.AuthKeyDto
}

interface ServerApi {

    /**
     * Anonymous health probe used by the "test connection" button.
     *
     * Returns the raw body on purpose: Kavita answers with the plain text `Ok` (not a JSON
     * string), so a JSON converter would fail to parse it. Retrofit handles [ResponseBody]
     * natively, bypassing content negotiation.
     */
    @GET("Health")
    suspend fun health(): ResponseBody

    @GET("Server/server-info-slim")
    suspend fun serverInfo(): ServerInfoSlimDto
}

interface LibraryApi {

    @GET("Library/libraries")
    suspend fun libraries(): List<LibraryDto>

    @GET("Library")
    suspend fun library(@Query("libraryId") libraryId: Int): LibraryDto
}

interface SeriesApi {

    @POST("Series/on-deck")
    suspend fun onDeck(
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("libraryId") libraryId: Int = 0,
    ): List<SeriesDto>

    @POST("Series/recently-updated-series")
    suspend fun recentlyUpdated(
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): List<GroupedSeriesDto>

    @POST("Series/recently-added-v2")
    suspend fun recentlyAdded(
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Body filter: SeriesFilterV2Dto = SeriesFilterV2Dto(),
    ): List<SeriesDto>

    @POST("Series/v2")
    suspend fun seriesForLibrary(
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 40,
        @Body filter: SeriesFilterV2Dto,
        /**
         * Wrapped in [retrofit2.Response] so the caller can read the `Pagination` header.
         * A `ThreadLocal` holder cannot work here: OkHttp writes it on its own dispatcher thread
         * while the coroutine resumes on another, so the value was always lost (AGENTS.md §4.3).
         */
    ): retrofit2.Response<List<SeriesDto>>

    @POST("Series/all-v2")
    suspend fun allSeries(
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 40,
        @Query("context") context: Int = 1,
        @Body filter: SeriesFilterV2Dto = SeriesFilterV2Dto(),
    ): List<SeriesDto>

    @GET("Series/{seriesId}")
    suspend fun series(@retrofit2.http.Path("seriesId") seriesId: Int): SeriesDto
}

interface StreamApi {

    @GET("Stream/dashboard")
    suspend fun dashboard(@Query("visibleOnly") visibleOnly: Boolean = true): List<DashboardStreamDto>
}
