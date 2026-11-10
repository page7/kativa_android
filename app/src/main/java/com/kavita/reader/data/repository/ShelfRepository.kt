package com.kavita.reader.data.repository

import com.kavita.reader.core.image.CoverUrlBuilder
import com.kavita.reader.core.network.ApiFactory
import com.kavita.reader.core.network.ErrorMapper
import com.kavita.reader.core.network.KavitaFailure
import com.kavita.reader.core.network.Outcome
import com.kavita.reader.data.mapper.ContentMapper
import com.kavita.reader.data.remote.api.UpdateWantToReadRequest
import com.kavita.reader.domain.model.SeriesSummary
import javax.inject.Inject
import javax.inject.Singleton

/** A collection or reading list row. */
data class ShelfCollection(
    val id: Int,
    val title: String,
    val subtitle: String?,
    val coverUrl: String?,
    val itemCount: Int,
    val promoted: Boolean,
)

/**
 * Collections, reading lists and Want To Read.
 *
 * All three are "shelves of series" from the reader's point of view, but they use three
 * different endpoints with different shapes — hence the explicit mapping in each function.
 */
@Singleton
class ShelfRepository @Inject constructor(
    private val apiFactory: ApiFactory,
    private val coverUrlBuilder: CoverUrlBuilder,
    private val seriesCache: SeriesListCache,
    private val activeServerStore: com.kavita.reader.core.auth.ActiveServerStore,
) {

    // --- collections ----------------------------------------------------

    suspend fun collections(): Outcome<List<ShelfCollection>> = try {
        Outcome.Success(
            apiFactory.collections().collections().map { dto ->
                ShelfCollection(
                    id = dto.id,
                    title = dto.title,
                    subtitle = dto.owner,
                    coverUrl = coverUrlBuilder.collection(dto.id),
                    itemCount = dto.itemCount,
                    promoted = dto.promoted,
                )
            },
        )
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    suspend fun seriesInCollection(collectionId: Int): Outcome<List<SeriesHit>> = try {
        Outcome.Success(
            apiFactory.collections().seriesInCollection(collectionId).map { dto ->
                val summary = ContentMapper.series(dto)
                SeriesHit(summary = summary, coverUrl = coverUrlBuilder.series(summary.id))
            },
        )
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    // --- reading lists --------------------------------------------------

    suspend fun readingLists(): Outcome<List<ShelfCollection>> = try {
        Outcome.Success(
            apiFactory.readingLists().lists().map { dto ->
                ShelfCollection(
                    id = dto.id,
                    title = dto.title,
                    subtitle = dto.ownerUserName,
                    coverUrl = coverUrlBuilder.readingList(dto.id),
                    itemCount = dto.itemCount,
                    promoted = dto.promoted,
                )
            },
        )
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    // --- want to read ---------------------------------------------------

    suspend fun wantToRead(page: Int = 1, pageSize: Int = 40): Outcome<List<SeriesHit>> = try {
        Outcome.Success(
            apiFactory.wantToRead().series(pageNumber = page, pageSize = pageSize).map { dto ->
                val summary = ContentMapper.series(dto)
                SeriesHit(summary = summary, coverUrl = coverUrlBuilder.series(summary.id))
            },
        )
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    /**
     * Kavita returns the literal text `true`/`false` for this endpoint, so we compare the body
     * rather than parsing JSON (docs/API.md §13.8).
     */
    suspend fun isInWantToRead(seriesId: Int): Boolean =
        runCatching { apiFactory.wantToRead().isInList(seriesId).trim().equals("true", true) }
            .getOrDefault(false)

    suspend fun addToWantToRead(seriesId: Int): Outcome<Unit> = try {
        apiFactory.wantToRead().addSeries(UpdateWantToReadRequest(listOf(seriesId)))
        invalidateSeriesCaches()
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    suspend fun removeFromWantToRead(seriesId: Int): Outcome<Unit> = try {
        apiFactory.wantToRead().removeSeries(UpdateWantToReadRequest(listOf(seriesId)))
        invalidateSeriesCaches()
        Outcome.Success(Unit)
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    suspend fun toggleWantToRead(seriesId: Int, currentlyInList: Boolean): Outcome<Unit> =
        if (currentlyInList) removeFromWantToRead(seriesId) else addToWantToRead(seriesId)

    /**
     * "Favourite" is backed by the want-to-read list, and a library grid can be filtered by it,
     * so a cached page from before the toggle would keep showing the old membership for up to
     * [SeriesListCache.TTL_MS] — a filtered grid looked empty even after adding the series.
     */
    private suspend fun invalidateSeriesCaches() {
        activeServerStore.activeId?.let { seriesCache.clearServer(it) }
    }
}

/** A series plus its resolved cover URL — the shape every shelf screen renders. */
data class SeriesHit(
    val summary: SeriesSummary,
    val coverUrl: String?,
)
