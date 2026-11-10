package com.kavita.reader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kavita encodes sentinels in the numeric fields: `-100000` = "no number parsed" and
 * `100000` = the specials container volume. Leaking them into the UI produced a real bug
 * (`Vol. -100000` / `Ch. -100000`), so every rule is pinned here.
 *
 * Reference: `Kavita.Models/Constants/ParserConstants.cs`.
 */
class KavitaNumberingTest {

    private fun chapter(
        id: Int = 1,
        minNumber: Float = 1f,
        number: String = "1",
        range: String? = null,
        title: String = "",
        isSpecial: Boolean = false,
    ) = Chapter(
        id = id,
        title = title,
        number = number,
        range = range,
        volumeId = 1,
        volumeTitle = null,
        pages = 10,
        pagesRead = 0,
        isSpecial = isSpecial,
        sortOrder = minNumber,
        releaseDate = null,
        wordCount = 0,
        minHoursToRead = 0,
        maxHoursToRead = 0,
        minNumber = minNumber,
    )

    private fun volume(
        id: Int = 1,
        minNumber: Float = 1f,
        name: String = "",
        chapters: List<Chapter> = emptyList(),
    ) = Volume(
        id = id,
        number = minNumber.toInt(),
        name = name,
        pages = chapters.sumOf { it.pages },
        pagesRead = 0,
        chapters = chapters,
        minNumber = minNumber,
    )

    // --- sentinels -------------------------------------------------------

    @Test
    fun `unnumbered chapter is detected`() {
        assertTrue(chapter(minNumber = -100_000f, number = "-100000").isUnnumbered)
    }

    @Test
    fun `regular chapter is not unnumbered`() {
        assertFalse(chapter(minNumber = 12f, number = "12").isUnnumbered)
    }

    @Test
    fun `volume zero is a real volume, not a sentinel`() {
        // Kavita moved loose-leaf from 0 to -100000 precisely so Volume 0 is representable.
        val v = volume(minNumber = 0f)
        assertFalse(v.isLooseLeaf)
        assertFalse(v.isSpecials)
        assertTrue(v.isRegular)
    }

    @Test
    fun `loose leaf volume is detected and excluded from regular volumes`() {
        assertTrue(volume(minNumber = -100_000f).isLooseLeaf)
        assertFalse(volume(minNumber = -100_000f).isRegular)
    }

    @Test
    fun `specials volume is detected and excluded from regular volumes`() {
        assertTrue(volume(minNumber = 100_000f).isSpecials)
        assertFalse(volume(minNumber = 100_000f).isRegular)
    }

    // --- display numbers -------------------------------------------------

    @Test
    fun `sentinel numbers never become a display number`() {
        assertNull(chapter(minNumber = -100_000f, number = "-100000").displayNumber)
        assertNull(chapter(minNumber = -100_000f, number = "-100000", range = "-100000").displayNumber)
    }

    @Test
    fun `range wins over number when present`() {
        assertEquals("5-7", chapter(number = "5", range = "5-7").displayNumber)
    }

    @Test
    fun `plain number is used when there is no range`() {
        assertEquals("12", chapter(number = "12").displayNumber)
    }

    @Test
    fun `float number keeps its decimal`() {
        assertEquals("12.5", EntityNumberingFacts.asNumber(chapter(number = "12.5").displayNumber))
    }

    // --- series aggregation ----------------------------------------------

    @Test
    fun `regular, loose leaf and specials are separated`() {
        val detail = SeriesDetail(
            seriesId = 1,
            name = "S",
            sortName = "S",
            libraryId = 1,
            libraryName = "L",
            format = ContentFormat.Archive,
            pages = 100,
            pagesRead = 0,
            volumes = listOf(
                volume(id = 10, minNumber = 1f, chapters = listOf(chapter(id = 1, minNumber = 1f))),
                volume(id = 11, minNumber = 2f, chapters = listOf(chapter(id = 2, minNumber = 2f))),
                volume(
                    id = 12,
                    minNumber = -100_000f,
                    chapters = listOf(chapter(id = 3, minNumber = -100_000f, number = "-100000")),
                ),
                volume(
                    id = 13,
                    minNumber = 100_000f,
                    chapters = listOf(chapter(id = 4, minNumber = 100_000f, isSpecial = true, title = "Bonus")),
                ),
            ),
        )

        assertEquals(listOf(10, 11), detail.regularVolumes.map { it.id })
        assertEquals(listOf(3), detail.looseLeafChapters.map { it.id })
        assertEquals(listOf(4), detail.specialChapters.map { it.id })
        // Totals still include everything, so progress is not under-reported.
        assertEquals(4, detail.totalChapters)
    }

    @Test
    fun `regular volumes are ordered numerically`() {
        val detail = SeriesDetail(
            seriesId = 1,
            name = "S",
            sortName = "S",
            libraryId = 1,
            libraryName = "L",
            format = ContentFormat.Archive,
            pages = 0,
            pagesRead = 0,
            volumes = listOf(
                volume(id = 20, minNumber = 10f),
                volume(id = 21, minNumber = 9f),
            ),
        )
        assertEquals(listOf(21, 20), detail.regularVolumes.map { it.id })
    }
}

/** Helper kept separate so the test reads clearly. */
private object EntityNumberingFacts {
    fun asNumber(value: String?): String? = value
}
