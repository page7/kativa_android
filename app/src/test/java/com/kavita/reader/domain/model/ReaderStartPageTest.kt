package com.kavita.reader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bookmarks can carry a page index, and that index is **0-based** — the same space as
 * `Reader/image?page=`. The two failure modes pinned here are real:
 *  - adding a `- 1` (treating it as 1-based) opened the page *before* the bookmark;
 *  - trusting it blindly crashed the pager once a chapter was re-scanned and got shorter.
 */
class ReaderStartPageTest {

    @Test
    fun `no explicit page keeps saved progress`() {
        assertEquals(4, ReaderStartPage.resolve(null, totalPages = 213, progressPageIndex = 4))
    }

    @Test
    fun `bookmark page index is used as-is`() {
        assertEquals(4, ReaderStartPage.resolve(4, totalPages = 213, progressPageIndex = 100))
    }

    @Test
    fun `first page is a valid bookmark target`() {
        assertEquals(0, ReaderStartPage.resolve(0, totalPages = 213, progressPageIndex = 100))
    }

    @Test
    fun `out of range bookmark clamps to the last page`() {
        assertEquals(212, ReaderStartPage.resolve(9999, totalPages = 213, progressPageIndex = 0))
    }

    @Test
    fun `negative bookmark clamps to the first page`() {
        assertEquals(0, ReaderStartPage.resolve(-7, totalPages = 213, progressPageIndex = 50))
    }

    @Test
    fun `empty chapter never produces a negative index`() {
        assertEquals(0, ReaderStartPage.resolve(5, totalPages = 0, progressPageIndex = 0))
        assertEquals(0, ReaderStartPage.resolve(null, totalPages = 0, progressPageIndex = 3))
    }

    @Test
    fun `progress is clamped too`() {
        assertEquals(9, ReaderStartPage.resolve(null, totalPages = 10, progressPageIndex = 99))
    }
}
