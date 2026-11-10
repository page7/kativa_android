package com.kavita.reader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the bug where the series detail showed `-100000` as a chapter label.
 *
 * Root cause: Kavita's server pre-formats `ChapterDto.Title` into strings such as
 * `"Chapter -100000"` (see `EntityNamingService.FormatChapterTitle`), so a naive
 * "title is not blank → show title" path leaks the sentinel even though `minNumber` is handled.
 */
class SentinelTitleTest {

    private fun chapter(
        title: String = "",
        minNumber: Float = 1f,
        number: String = "1",
        range: String? = null,
        volumeTitle: String? = null,
        isSpecial: Boolean = false,
    ) = Chapter(
        id = 1,
        title = title,
        number = number,
        range = range,
        volumeId = 1,
        volumeTitle = volumeTitle,
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

    // --- the exact production bug -----------------------------------------

    @Test
    fun `server formatted title containing -100000 is not safe to display`() {
        val c = chapter(
            title = "Chapter -100000",
            minNumber = -100_000f,
            number = "-100000",
        )
        assertNull(c.safeTitle)
        assertNull(c.displayNumber)
        assertTrue(c.isUnnumbered)
    }

    @Test
    fun `server formatted title containing 100000 is not safe to display`() {
        val c = chapter(title = "Volume 100000", minNumber = 100_000f, isSpecial = true)
        assertNull(c.safeTitle)
    }

    @Test
    fun `a normal formatted title is safe`() {
        val c = chapter(title = "Chapter 1448 - The Big Fight", minNumber = 1448f, number = "1448")
        assertEquals("Chapter 1448 - The Big Fight", c.safeTitle)
    }

    @Test
    fun `sentinel detection does not false-positive on similar numbers`() {
        // 1000001 / 110000 / 10000 are legitimate numbers and must not be treated as sentinels.
        assertFalse(KavitaNumbering.containsSentinel("Chapter 1000001"))
        assertFalse(KavitaNumbering.containsSentinel("Chapter 110000"))
        assertFalse(KavitaNumbering.containsSentinel("Chapter 10000"))
        assertFalse(KavitaNumbering.containsSentinel("Chapter -10000"))
        assertTrue(KavitaNumbering.containsSentinel("Chapter 100000"))
        assertTrue(KavitaNumbering.containsSentinel("Volume -100000"))
    }

    @Test
    fun `clean title with no sentinel passes through`() {
        assertTrue(KavitaNumbering.containsSentinel("Chapter -100000"))
        assertFalse(KavitaNumbering.containsSentinel("Epilogue"))
        assertFalse(KavitaNumbering.containsSentinel(""))
        assertFalse(KavitaNumbering.containsSentinel(null))
    }

    // --- volume names -----------------------------------------------------

    @Test
    fun `volume name with sentinel is not safe`() {
        val v = Volume(
            id = 1,
            number = -100000,
            name = "Volume -100000",
            pages = 10,
            pagesRead = 0,
            chapters = emptyList(),
            minNumber = -100_000f,
        )
        assertNull(v.safeName)
        assertTrue(v.isLooseLeaf)
    }

    @Test
    fun `normal volume name is safe`() {
        val v = Volume(
            id = 1,
            number = 3,
            name = "Volume 3",
            pages = 10,
            pagesRead = 0,
            chapters = emptyList(),
            minNumber = 3f,
        )
        assertEquals("Volume 3", v.safeName)
    }

    @Test
    fun `a literal volume zero is kept`() {
        val v = Volume(
            id = 1,
            number = 0,
            name = "Volume 0",
            pages = 10,
            pagesRead = 0,
            chapters = emptyList(),
            minNumber = 0f,
        )
        assertEquals("Volume 0", v.safeName)
        assertTrue(v.isRegular)
    }
}
