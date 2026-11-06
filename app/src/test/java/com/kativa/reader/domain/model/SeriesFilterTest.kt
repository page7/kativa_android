package com.kativa.reader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The filter model maps 1:1 onto Kavita's `SeriesFilterV2Dto`. Wrong integers mean the server
 * silently returns nothing, so the enum values are pinned against `docs/API.md` §12.
 */
class SeriesFilterTest {

    @Test
    fun `field api values match Kavita`() {
        assertEquals(1, SeriesFilterField.SeriesName.apiValue)
        assertEquals(17, SeriesFilterField.Writers.apiValue)
        assertEquals(18, SeriesFilterField.Genres.apiValue)
        assertEquals(6, SeriesFilterField.Tags.apiValue)
        assertEquals(2, SeriesFilterField.PublicationStatus.apiValue)
        assertEquals(4, SeriesFilterField.AgeRating.apiValue)
        assertEquals(5, SeriesFilterField.UserRating.apiValue)
        assertEquals(20, SeriesFilterField.ReadProgress.apiValue)
        assertEquals(21, SeriesFilterField.Formats.apiValue)
        assertEquals(22, SeriesFilterField.ReleaseYear.apiValue)
        assertEquals(3, SeriesFilterField.Languages.apiValue)
        assertEquals(26, SeriesFilterField.WantToRead.apiValue)
    }

    @Test
    fun `comparison api values match Kavita`() {
        assertEquals(0, Comparison.Equal.apiValue)
        assertEquals(1, Comparison.GreaterThan.apiValue)
        assertEquals(2, Comparison.GreaterThanEqual.apiValue)
        assertEquals(3, Comparison.LessThan.apiValue)
        assertEquals(4, Comparison.LessThanEqual.apiValue)
        assertEquals(5, Comparison.Contains.apiValue)
        assertEquals(8, Comparison.NotContains.apiValue)
        assertEquals(9, Comparison.NotEqual.apiValue)
        assertEquals(10, Comparison.BeginsWith.apiValue)
        assertEquals(11, Comparison.EndsWith.apiValue)
        assertEquals(16, Comparison.IsEmpty.apiValue)
        assertEquals(17, Comparison.IsNotEmpty.apiValue)
        assertEquals(14, Comparison.IsInLast.apiValue)
    }

    @Test
    fun `sort api values match Kavita`() {
        assertEquals(1, SeriesSort.SortName.apiValue)
        assertEquals(2, SeriesSort.CreatedDate.apiValue)
        assertEquals(3, SeriesSort.LastModifiedDate.apiValue)
        assertEquals(4, SeriesSort.LastChapterAdded.apiValue)
        assertEquals(5, SeriesSort.TimeToRead.apiValue)
        assertEquals(6, SeriesSort.ReleaseYear.apiValue)
        assertEquals(7, SeriesSort.ReadProgress.apiValue)
        assertEquals(9, SeriesSort.Random.apiValue)
        assertEquals(10, SeriesSort.UserRating.apiValue)
        assertEquals(11, SeriesSort.UnreadChapterCount.apiValue)
    }

    @Test
    fun `combination maps to the server enum`() {
        assertEquals(1, SeriesFilter.Combination.And.apiValue)
        assertEquals(0, SeriesFilter.Combination.Or.apiValue)
    }

    @Test
    fun `default filter is empty and sorted by name`() {
        val filter = SeriesFilter.Default
        assertTrue(filter.isEmpty)
        assertEquals(0, filter.activeCount)
        assertEquals(SeriesSort.SortName, filter.sort)
        assertTrue(filter.ascending)
    }

    @Test
    fun `dto carries statements combination and sort`() {
        val filter = SeriesFilter(
            statements = listOf(
                SeriesFilter.Statement(SeriesFilterField.Writers, Comparison.Contains, "Urasawa"),
            ),
            combination = SeriesFilter.Combination.Or,
            sort = SeriesSort.ReleaseYear,
            ascending = false,
        )

        val dto = filter.toDto()
        assertEquals(1, dto.statements.size)
        assertEquals(17, dto.statements.first().field)
        assertEquals(5, dto.statements.first().comparison)
        assertEquals("Urasawa", dto.statements.first().value)
        assertEquals(0, dto.combination)
        assertEquals(6, dto.sortOptions?.sortField)
        assertEquals(false, dto.sortOptions?.isAscending)
    }

    @Test
    fun `removing a statement keeps the rest and the sort`() {
        val filter = SeriesFilter(
            statements = listOf(
                SeriesFilter.Statement(SeriesFilterField.SeriesName, Comparison.Contains, "a"),
                SeriesFilter.Statement(SeriesFilterField.Genres, Comparison.Equal, "Action"),
            ),
            sort = SeriesSort.ReadProgress,
        )

        val reduced = filter.withoutStatement(0)
        assertEquals(1, reduced.statements.size)
        assertEquals(SeriesFilterField.Genres, reduced.statements.first().field)
        assertEquals(SeriesSort.ReadProgress, reduced.sort)
    }

    @Test
    fun `comparisons that need no value are flagged`() {
        assertFalse(Comparison.IsEmpty.needsValue)
        assertFalse(Comparison.IsNotEmpty.needsValue)
        assertTrue(Comparison.Contains.needsValue)
        assertTrue(Comparison.Equal.needsValue)
    }

    @Test
    fun `field-comparison compatibility rejects nonsense combinations`() {
        // "is empty" is meaningless for an enum-like field.
        assertFalse(Comparison.IsEmpty.allowedFor(SeriesFilterField.PublicationStatus))
        assertTrue(Comparison.Equal.allowedFor(SeriesFilterField.PublicationStatus))
        // Numeric comparison is fine for a year.
        assertTrue(Comparison.GreaterThan.allowedFor(SeriesFilterField.ReleaseYear))
        // But not for a name.
        assertFalse(Comparison.GreaterThan.allowedFor(SeriesFilterField.SeriesName))
    }

    @Test
    fun `statement describe is readable`() {
        val statement = SeriesFilter.Statement(
            SeriesFilterField.SeriesName,
            Comparison.Contains,
            "Berserk",
        )
        assertEquals("Series name contains Berserk", statement.describe())
    }
}
