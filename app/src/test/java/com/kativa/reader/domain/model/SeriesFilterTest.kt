package com.kativa.reader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the small filter model against Kavita's wire contract (`docs/API.md` §12).
 *
 * The field/comparison/sort integers are the whole contract: a wrong one is either silently
 * ignored or, worse, rejected per field by the server's `ComparisonProfile` validation, which
 * aborts the request instead of returning no rows. That is exactly what happened with the old
 * name search, which sent `Contains(5)` for `SeriesName`.
 */
class SeriesFilterTest {

    @Test
    fun `field api values match Kavita`() {
        assertEquals(1, SeriesFilterField.SeriesName.apiValue)
        assertEquals(26, SeriesFilterField.WantToRead.apiValue)
    }

    @Test
    fun `comparison api values match Kavita`() {
        assertEquals(0, Comparison.Equal.apiValue)
        // `Matches` is SQL LIKE %value% and is the ONLY substring comparison allowed for names.
        assertEquals(7, Comparison.Matches.apiValue)
    }

    @Test
    fun `sort api values match Kavita`() {
        assertEquals(1, SeriesSort.SortName.apiValue)
        assertEquals(2, SeriesSort.CreatedDate.apiValue)
        assertEquals(7, SeriesSort.ReadProgress.apiValue)
    }

    @Test
    fun `default filter is empty, sorted by name ascending`() {
        val filter = SeriesFilter.Default
        assertTrue(filter.isEmpty)
        assertEquals(0, filter.activeCount)
        assertEquals(SeriesSort.SortName, filter.sort)
        assertTrue(filter.ascending)
        assertEquals("", filter.nameQuery)
        assertNull(filter.wantToRead)
    }

    @Test
    fun `name query is sent as a Matches statement`() {
        val filter = SeriesFilter.Default.withNameQuery("  Berserk  ")

        assertEquals("Berserk", filter.nameQuery)
        val dto = filter.toDto()
        assertEquals(1, dto.statements.size)
        assertEquals(1, dto.statements.first().field)
        assertEquals(7, dto.statements.first().comparison)
        assertEquals("Berserk", dto.statements.first().value)
    }

    @Test
    fun `blank name query removes the statement`() {
        val filter = SeriesFilter.Default.withNameQuery("abc").withNameQuery("   ")
        assertTrue(filter.isEmpty)
        assertTrue(filter.toDto().statements.isEmpty())
    }

    @Test
    fun `want to read is sent as a boolean string`() {
        assertEquals("true", SeriesFilter.Default.withWantToRead(true).toDto().statements.single().value)
        assertEquals("false", SeriesFilter.Default.withWantToRead(false).toDto().statements.single().value)
        assertEquals(26, SeriesFilter.Default.withWantToRead(true).toDto().statements.single().field)
    }

    @Test
    fun `clearing want to read removes its statement`() {
        val filter = SeriesFilter.Default.withWantToRead(true).withWantToRead(null)
        assertTrue(filter.isEmpty)
        assertNull(filter.wantToRead)
    }

    @Test
    fun `name and want to read coexist without duplicating a field`() {
        val filter = SeriesFilter.Default
            .withNameQuery("Berserk")
            .withWantToRead(true)
            .withNameQuery("Vagabond")

        assertEquals(2, filter.statements.size)
        assertEquals("Vagabond", filter.nameQuery)
        assertEquals(true, filter.wantToRead)

        // Clicking the same chip twice must not stack two statements for the same field.
        val replaced = filter.withNameQuery("Berserk")
        assertEquals(2, replaced.statements.size)
        assertEquals(1, replaced.statements.count { it.field == SeriesFilterField.SeriesName })
    }

    @Test
    fun `dto pins the And combination so a filter cannot escape the library scope`() {
        // An `Or` combination would union the library statement away; the model has no knob for
        // it, and the repository relies on that.
        val dto = SeriesFilter.Default.withNameQuery("a").toDto()
        assertEquals(1, dto.combination)
        assertEquals(SeriesFilter.COMBINATION_AND, dto.combination)
    }

    @Test
    fun `library scope statement comes first and keeps the user conditions`() {
        val dto = SeriesFilter.Default
            .withNameQuery("Berserk")
            .withWantToRead(true)
            .toLibraryScopedDto(libraryId = 5)

        assertEquals(1, dto.combination)
        assertEquals(3, dto.statements.size)
        // Scope first, and `Libraries` is compared with Equal(0) against the library id.
        assertEquals(19, dto.statements[0].field)
        assertEquals(0, dto.statements[0].comparison)
        assertEquals("5", dto.statements[0].value)
        // User conditions survive untouched.
        assertTrue(dto.statements.any { it.field == 1 && it.value == "Berserk" })
        assertTrue(dto.statements.any { it.field == 26 && it.value == "true" })
    }

    @Test
    fun `sort and direction travel in sortOptions`() {
        val dto = SeriesFilter.Default.withSort(SeriesSort.ReadProgress, ascending = false).toDto()
        assertEquals(7, dto.sortOptions?.sortField)
        assertEquals(false, dto.sortOptions?.isAscending)
    }

    @Test
    fun `each sort field has a natural direction`() {
        // Names read A→Z; the date-ish sorts are only useful newest-first.
        assertTrue(SeriesSort.SortName.defaultAscending)
        assertFalse(SeriesSort.CreatedDate.defaultAscending)
        assertFalse(SeriesSort.ReadProgress.defaultAscending)
    }

    @Test
    fun `switching sort field adopts its natural direction`() {
        // Regression: the direction was carried across fields, so picking "date added" while
        // "ascending" was selected showed the oldest imports first.
        val byNewest = SeriesFilter.Default.withSort(SeriesSort.CreatedDate)
        assertFalse(byNewest.ascending)

        val backToName = byNewest.withSort(SeriesSort.SortName)
        assertEquals(SeriesSort.SortName, backToName.sort)
        assertTrue(backToName.ascending)
    }

    @Test
    fun `explicit direction wins over the natural default`() {
        val oldest = SeriesFilter.Default.withSort(SeriesSort.CreatedDate, ascending = true)
        assertTrue(oldest.ascending)
        // ...and it is what gets sent, not the default.
        assertEquals(true, oldest.toDto().sortOptions?.isAscending)
    }

    @Test
    fun `changing sort keeps the active conditions`() {
        val filter = SeriesFilter.Default.withNameQuery("a").withSort(SeriesSort.CreatedDate, false)
        assertEquals("a", filter.nameQuery)
        assertEquals(SeriesSort.CreatedDate, filter.sort)
        assertFalse(filter.ascending)
    }
    @Test
    fun `removing a statement keeps the rest and the sort`() {
        val filter = SeriesFilter.Default
            .withNameQuery("a")
            .withWantToRead(true)
            .withSort(SeriesSort.ReadProgress)

        val withoutName = filter.withNameQuery("")
        assertEquals(1, withoutName.statements.size)
        assertEquals(SeriesSort.ReadProgress, withoutName.sort)
    }
}
