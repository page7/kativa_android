package com.kativa.reader.domain.model

import com.kativa.reader.data.remote.dto.SeriesFilterStatementDto
import com.kativa.reader.data.remote.dto.SeriesFilterV2Dto
import com.kativa.reader.data.remote.dto.SeriesSortOptionDto

/**
 * Server-side series filter, mirroring Kavita's `SeriesFilterV2Dto`.
 *
 * Field/comparison values are the raw integers from `docs/API.md` §12 — do not invent new
 * values, the server will silently return nothing.
 */
data class SeriesFilter(
    val statements: List<Statement> = emptyList(),
    val combination: Combination = Combination.And,
    val sort: SeriesSort = SeriesSort.SortName,
    val ascending: Boolean = true,
) {
    val isEmpty: Boolean get() = statements.isEmpty()

    val activeCount: Int get() = statements.size

    enum class Combination(val apiValue: Int) {
        /** All statements must match. */
        And(1),

        /** Any statement may match. */
        Or(0),
    }

    /**
     * One filter row. [value] is a string because the API takes strings for every field type
     * (numbers, ids, enums and dates alike).
     */
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

        /** Human-readable summary, used by the chips above the grid. */
        fun describe(): String = "${field.label} ${comparison.label} $value"
    }

    fun toDto(): SeriesFilterV2Dto = SeriesFilterV2Dto(
        statements = statements.map { it.toDto() },
        combination = combination.apiValue,
        sortOptions = SeriesSortOptionDto(sortField = sort.apiValue, isAscending = ascending),
    )

    fun withSort(sort: SeriesSort, ascending: Boolean): SeriesFilter =
        copy(sort = sort, ascending = ascending)

    fun withStatements(statements: List<Statement>, combination: Combination = this.combination): SeriesFilter =
        copy(statements = statements, combination = combination)

    fun withoutStatement(index: Int): SeriesFilter =
        copy(statements = statements.filterIndexed { i, _ -> i != index })

    companion object {
        val Default = SeriesFilter()
    }
}

/**
 * `SeriesFilterField` values (docs/API.md §12).
 *
 * Only fields that make sense on a phone are exposed; the server accepts many more.
 */
enum class SeriesFilterField(val apiValue: Int, val label: String) {
    SeriesName(1, "Series name"),
    Writers(17, "Writer"),
    Genres(18, "Genre"),
    Tags(6, "Tag"),
    PublicationStatus(2, "Status"),
    AgeRating(4, "Age rating"),
    UserRating(5, "My rating"),
    ReadProgress(20, "Read progress"),
    Formats(21, "Format"),
    ReleaseYear(22, "Release year"),
    Languages(3, "Language"),
    WantToRead(26, "Want to read"),
    ;

    companion object {
        fun from(apiValue: Int): SeriesFilterField? = entries.firstOrNull { it.apiValue == apiValue }
    }
}

/** `FilterComparison` values (docs/API.md §12). */
enum class Comparison(val apiValue: Int, val label: String) {
    Equal(0, "is"),
    NotEqual(9, "is not"),
    Contains(5, "contains"),
    NotContains(8, "doesn't contain"),
    GreaterThan(1, ">"),
    GreaterThanEqual(2, ">="),
    LessThan(3, "<"),
    LessThanEqual(4, "<="),
    BeginsWith(10, "starts with"),
    EndsWith(11, "ends with"),
    IsEmpty(16, "is empty"),
    IsNotEmpty(17, "is not empty"),
    IsInLast(14, "in the last (days)"),
    ;

    /** Comparisons that need no user-entered value. */
    val needsValue: Boolean
        get() = this != IsEmpty && this != IsNotEmpty

    /** The comparisons that actually work for a given field on Kavita. */
    fun allowedFor(field: SeriesFilterField): Boolean = when (field) {
        SeriesFilterField.SeriesName,
        SeriesFilterField.Writers,
        SeriesFilterField.Genres,
        SeriesFilterField.Tags,
        SeriesFilterField.Languages,
        -> this in setOf(Equal, NotEqual, Contains, NotContains, BeginsWith, EndsWith, IsEmpty, IsNotEmpty)

        SeriesFilterField.PublicationStatus,
        SeriesFilterField.AgeRating,
        SeriesFilterField.Formats,
        SeriesFilterField.WantToRead,
        -> this in setOf(Equal, NotEqual)

        SeriesFilterField.UserRating,
        SeriesFilterField.ReadProgress,
        SeriesFilterField.ReleaseYear,
        -> this in setOf(Equal, NotEqual, GreaterThan, GreaterThanEqual, LessThan, LessThanEqual)

        else -> true
    }
}

/** `SeriesSortField` values (docs/API.md §12). */
enum class SeriesSort(val apiValue: Int, val label: String) {
    SortName(1, "Name"),
    CreatedDate(2, "Date added"),
    LastModifiedDate(3, "Date modified"),
    LastChapterAdded(4, "Chapter added"),
    TimeToRead(5, "Time to read"),
    ReleaseYear(6, "Release year"),
    ReadProgress(7, "Read progress"),
    UserRating(10, "My rating"),
    UnreadChapterCount(11, "Unread chapters"),
    Random(9, "Random"),
    ;

    companion object {
        fun from(apiValue: Int): SeriesSort = entries.firstOrNull { it.apiValue == apiValue } ?: SortName
    }
}
