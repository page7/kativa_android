package com.kativa.reader.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Folder preview rules.
 *
 * The tile shows four covers in the library's *default* order. Two things must hold:
 *  - never request/emit more than four (each is a separate image load);
 *  - always reserve four slots so a small library does not produce a lopsided tile.
 */
class LibraryFolderPreviewTest {

    /** Mirrors the mapping in HomeViewModel so the rules are pinned independently of Compose. */
    private fun previewOf(covers: List<String?>): List<String?> = List(4) { covers.getOrNull(it) }

    @Test
    fun `takes at most four covers`() {
        val many = List(10) { "cover-$it" }
        val preview = previewOf(many.take(4))
        assertEquals(4, preview.size)
        assertEquals(listOf("cover-0", "cover-1", "cover-2", "cover-3"), preview)
    }

    @Test
    fun `keeps the server order, does not sort`() {
        // "Zebra" first: the server's default (sort name) decides, the UI must not re-sort.
        val covers = listOf("zebra", "apple", "mango")
        assertEquals(listOf("zebra", "apple", "mango", null), previewOf(covers))
    }

    @Test
    fun `pads a small library with empty slots`() {
        assertEquals(listOf("a", null, null, null), previewOf(listOf("a")))
        assertEquals(listOf("a", "b", null, null), previewOf(listOf("a", "b")))
        assertEquals(listOf("a", "b", "c", null), previewOf(listOf("a", "b", "c")))
    }

    @Test
    fun `empty library yields four empty slots, not an empty list`() {
        val preview = previewOf(emptyList())
        assertEquals(4, preview.size)
        assertTrue(preview.all { it == null })
    }

    @Test
    fun `exactly four covers need no padding`() {
        assertEquals(listOf("a", "b", "c", "d"), previewOf(listOf("a", "b", "c", "d")))
    }
}
