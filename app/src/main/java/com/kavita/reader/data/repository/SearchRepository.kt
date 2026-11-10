package com.kavita.reader.data.repository

import com.kavita.reader.core.image.CoverUrlBuilder
import com.kavita.reader.core.network.ApiFactory
import com.kavita.reader.core.network.ErrorMapper
import com.kavita.reader.core.network.KavitaFailure
import com.kavita.reader.core.network.Outcome
import com.kavita.reader.data.remote.dto.SearchResultDto
import com.kavita.reader.data.remote.dto.SearchResultGroupDto
import javax.inject.Inject
import javax.inject.Singleton

/** One hit in a search result group, already mapped to display fields. */
data class SearchHit(
    val id: Int,
    val title: String,
    val subtitle: String?,
    val coverUrl: String?,
    val kind: SearchKind,
)

enum class SearchKind { Series, Collection, ReadingList, Person, Genre, Tag, Library }

/** Search results, grouped and ordered so the UI can render sections without re-sorting. */
data class SearchResults(
    val query: String,
    val groups: List<SearchGroup>,
) {
    val isEmpty: Boolean get() = groups.all { it.hits.isEmpty() }
    val totalHits: Int get() = groups.sumOf { it.hits.size }
}

data class SearchGroup(
    val kind: SearchKind,
    val hits: List<SearchHit>,
)

@Singleton
class SearchRepository @Inject constructor(
    private val apiFactory: ApiFactory,
    private val coverUrlBuilder: CoverUrlBuilder,
) {

    /**
     * Searches series/collections/reading lists/people/genres/tags.
     *
     * Chapter and file hits are excluded: `includeChapterAndFiles=false` keeps the payload small
     * and those hits are not actionable on mobile (docs/API.md §6).
     */
    suspend fun search(query: String): Outcome<SearchResults> = try {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            Outcome.Success(SearchResults(trimmed, emptyList()))
        } else {
            val group = apiFactory.search().search(
                queryString = trimmed,
                includeChapterAndFiles = false,
            )
            Outcome.Success(toResults(trimmed, group))
        }
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    suspend fun collections(): Outcome<List<SearchHit>> = try {
        Outcome.Success(
            apiFactory.collections().collections().map { dto ->
                SearchHit(
                    id = dto.id,
                    title = dto.title,
                    subtitle = dto.itemCount.takeIf { it > 0 }?.let { "$it" },
                    coverUrl = coverUrlBuilder.collection(dto.id),
                    kind = SearchKind.Collection,
                )
            },
        )
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    suspend fun readingLists(): Outcome<List<SearchHit>> = try {
        Outcome.Success(
            apiFactory.readingLists().lists().map { dto ->
                SearchHit(
                    id = dto.id,
                    title = dto.title,
                    subtitle = dto.ownerUserName,
                    coverUrl = coverUrlBuilder.readingList(dto.id),
                    kind = SearchKind.ReadingList,
                )
            },
        )
    } catch (t: Throwable) {
        Outcome.Failure(KavitaFailure(ErrorMapper.from(t)))
    }

    private fun toResults(query: String, group: SearchResultGroupDto): SearchResults {
        val groups = buildList {
            seriesGroup(group.series)?.let(::add)
            collectionGroup(group.collections)?.let(::add)
            readingListGroup(group.readingLists)?.let(::add)
            personGroup(group.persons)?.let(::add)
            genreGroup(group.genres)?.let(::add)
            tagGroup(group.tags)?.let(::add)
        }
        return SearchResults(query, groups)
    }

    private fun seriesGroup(items: List<SearchResultDto>): SearchGroup? {
        val hits = items.map { dto ->
            SearchHit(
                id = dto.seriesId,
                title = dto.localizedName?.takeIf { it.isNotBlank() } ?: dto.name,
                subtitle = listOfNotNull(
                    dto.libraryName?.takeIf { it.isNotBlank() },
                    dto.releaseYear?.takeIf { it > 0 }?.toString(),
                ).joinToString(" · ").ifBlank { null },
                coverUrl = coverUrlBuilder.series(dto.seriesId),
                kind = SearchKind.Series,
            )
        }
        return hits.takeIf { it.isNotEmpty() }?.let { SearchGroup(SearchKind.Series, it) }
    }

    private fun collectionGroup(items: List<SearchResultDto>): SearchGroup? =
        items.map { dto ->
            SearchHit(
                id = dto.seriesId,
                title = dto.localizedName?.takeIf { it.isNotBlank() } ?: dto.name,
                subtitle = dto.libraryName,
                coverUrl = coverUrlBuilder.collection(dto.seriesId),
                kind = SearchKind.Collection,
            )
        }.takeIf { it.isNotEmpty() }?.let { SearchGroup(SearchKind.Collection, it) }

    private fun readingListGroup(items: List<SearchResultDto>): SearchGroup? =
        items.map { dto ->
            SearchHit(
                id = dto.seriesId,
                title = dto.localizedName?.takeIf { it.isNotBlank() } ?: dto.name,
                subtitle = dto.libraryName,
                coverUrl = coverUrlBuilder.readingList(dto.seriesId),
                kind = SearchKind.ReadingList,
            )
        }.takeIf { it.isNotEmpty() }?.let { SearchGroup(SearchKind.ReadingList, it) }

    private fun personGroup(items: List<SearchResultDto>): SearchGroup? =
        items.map { dto ->
            SearchHit(
                id = dto.seriesId,
                title = dto.name,
                subtitle = null,
                coverUrl = coverUrlBuilder.person(dto.seriesId),
                kind = SearchKind.Person,
            )
        }.takeIf { it.isNotEmpty() }?.let { SearchGroup(SearchKind.Person, it) }

    private fun genreGroup(items: List<SearchResultDto>): SearchGroup? =
        items.map { dto ->
            SearchHit(dto.seriesId, dto.name, null, null, SearchKind.Genre)
        }.takeIf { it.isNotEmpty() }?.let { SearchGroup(SearchKind.Genre, it) }

    private fun tagGroup(items: List<SearchResultDto>): SearchGroup? =
        items.map { dto ->
            SearchHit(dto.seriesId, dto.name, null, null, SearchKind.Tag)
        }.takeIf { it.isNotEmpty() }?.let { SearchGroup(SearchKind.Tag, it) }

    companion object {
        /**
         * Minimum characters before a request is issued.
         *
         * 1, not 2: Kavita's `/Search/search` happily matches a single character (verified against
         * a live server: `z` → 2 series, `日` → 6). An empty/blank query is the only thing the
         * server rejects (400), and [SearchViewModel] never sends one. The old 2-character gate
         * silently swallowed every single-character query, which made search feel broken.
         */
        const val MIN_QUERY_LENGTH = 1
    }
}
