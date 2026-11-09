package com.kativa.reader.domain.model

import com.kativa.reader.data.remote.dto.SeriesFilterStatementDto
import com.kativa.reader.data.remote.dto.SeriesFilterV2Dto
import com.kativa.reader.data.remote.dto.SeriesSortOptionDto

/**
 * Server-side series filter, mirroring Kavita's `SeriesFilterV2Dto`.
 *
 * Deliberately small: the inside-a-library grid only exposes sort, "want to read" and a name
 * query (product decision, `docs/FEATURES.md` F-13). Field/comparison numbers are the raw
 * integers from `docs/API.md` §12 — do not invent values, the server rejects some of them.
 *
 * **Comparisons are validated per field server-side** (`ComparisonProfile`), and a rejected
 * comparison throws instead of returning no rows. Name matching therefore uses
 * [Comparison.Matches] (SQL `LIKE %value%`), never `Contains(5)` — that one is only valid for
 * list-shaped fields and made every name search fail with a 500.
 */
data class SeriesFilter(
    val statements: List<Statement> = emptyList(),
    val sort: SeriesSort = SeriesSort.SortName,
    val ascending: Boolean = true,
) {
    val isEmpty: Boolean get() = statements.isEmpty()

    val activeCount: Int get() = statements.size

    /** Free-text series name query, empty when the user typed nothing. */
    val nameQuery: String
        get() = statements.firstOrNull { it.field == SeriesFilterField.SeriesName }?.value.orEmpty()

    /** `null` = 不限制，`true` = 仅想读，`false` = 排除想读。 */
    val wantToRead: Boolean?
        get() = statements.firstOrNull { it.field == SeriesFilterField.WantToRead }
            ?.value
            ?.toBooleanStrictOrNull()

    /** One filter row. [value] is a string because the API takes strings for every field type. */
    data class Statement(
        val field: SeriesFilterField,
        val comparison: Comparison,
        val value: String,
    ) {
        fun toDto() = SeriesFilterStatementDto(
            field = field.apiValue,
            comparison = comparison.apiValue,
            value = value,
        )
    }

    /**
     * The combination is fixed to `And(1)`: the library scope statement is injected separately
     * (see `LibraryRepository`) and an `Or` user statement would escape that scope.
     */
    fun toDto(): SeriesFilterV2Dto = SeriesFilterV2Dto(
        statements = statements.map { it.toDto() },
        combination = COMBINATION_AND,
        sortOptions = SeriesSortOptionDto(sortField = sort.apiValue, isAscending = ascending),
    )

    /**
     * Body for `POST /api/Series/v2` inside one library.
     *
     * The library restriction goes first and the combination is pinned to `And`, so a user
     * condition can never union the library scope away. `WantToRead(26)` is pulled out by the
     * server in a pre-pass (`SeriesRepository.ApplyWantToReadFilter`) before the And/Or group is
     * applied, so it must be present as a sibling statement in this same flat list.
     */
    fun toLibraryScopedDto(libraryId: Int): SeriesFilterV2Dto = toDto().copy(
        statements = listOf(
            SeriesFilterStatementDto(
                field = FIELD_LIBRARIES,
                comparison = Comparison.Equal.apiValue,
                value = libraryId.toString(),
            ),
        ) + statements.map { it.toDto() },
        combination = COMBINATION_AND,
    )

    /**
     * Changes the sort field and direction.
     *
     * [ascending] defaults to the field's natural direction (A→Z for names, newest first for
     * dates) so "sort by date added" does not silently keep a stale "ascending" flag.
     */
    fun withSort(sort: SeriesSort, ascending: Boolean = sort.defaultAscending): SeriesFilter =
        copy(sort = sort, ascending = ascending)

    /** Sets (or clears, for a blank query) the series-name condition. */
    fun withNameQuery(query: String): SeriesFilter = copy(
        statements = replacing(
            SeriesFilterField.SeriesName,
            query.trim()
                .takeIf { it.isNotEmpty() }
                ?.let { Statement(SeriesFilterField.SeriesName, Comparison.Matches, it) },
        ),
    )

    /** `null` removes the condition, `true` keeps only want-to-read, `false` excludes it. */
    fun withWantToRead(value: Boolean?): SeriesFilter = copy(
        statements = replacing(
            SeriesFilterField.WantToRead,
            value?.let { Statement(SeriesFilterField.WantToRead, Comparison.Equal, it.toString()) },
        ),
    )

    /** Keeps every other field's condition but the one being replaced. */
    private fun replacing(field: SeriesFilterField, statement: Statement?): List<Statement> =
        statements.filterNot { it.field == field } + listOfNotNull(statement)

    companion object {
        val Default = SeriesFilter()

        /** `FilterCombination.And` (docs/API.md §12). */
        const val COMBINATION_AND = 1

        /** `SeriesFilterField.Libraries` — library scope statement (docs/API.md §12). */
        const val FIELD_LIBRARIES = 19
    }
}

/**
 * `SeriesFilterField` values used by the app (docs/API.md §12).
 *
 * Local-only scope: the grid never filters by tags/genres; adding them back means re-checking
 * each field's allowed comparisons in the server's `ComparisonProfile`.
 */
enum class SeriesFilterField(val apiValue: Int) {
    SeriesName(1),
    WantToRead(26),
}

/**
 * `FilterComparison` values used by the app (docs/API.md §12).
 *
 * `Matches` is a SQL `LIKE %value%` and is the only substring comparison the server accepts for
 * the string-shaped `SeriesName` field.
 */
enum class Comparison(val apiValue: Int) {
    Equal(0),
    Matches(7),
}

/** `SeriesSortField` values exposed in the UI (docs/API.md §12). */
enum class SeriesSort(val apiValue: Int, val defaultAscending: Boolean) {
    /** `SortName` — alphabetical. */
    SortName(1, true),

    /** `CreatedDate` — when the series was imported. */
    CreatedDate(2, false),

    /** `ReadProgress` — the user's last reading date. */
    ReadProgress(7, false),
}
