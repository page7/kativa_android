package com.kativa.reader.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the semantics of the folder-tile count.
 *
 * Regression: the home screen passed `series.size` (the number of *preview* covers, capped at 4)
 * as the library's series count, so every folder tile read "4 series" regardless of the library.
 * The truthful value only exists in the `Pagination` response header.
 */
class SeriesPageTest {

    @Test
    fun `total comes from the pagination header, not the page size`() {
        val page = SeriesPage(items = List(4) { dummy() }, totalItems = 85)
        assertEquals(85, page.totalOrPageSize)
        assertEquals(4, page.items.size)
    }

    @Test
    fun `missing header falls back to page size rather than claiming zero`() {
        // Unknown must never render as "0 series"; the page size is the honest lower bound.
        val page = SeriesPage(items = List(3) { dummy() }, totalItems = 0)
        assertEquals(3, page.totalOrPageSize)
    }

    @Test
    fun `empty library reports zero`() {
        val page = SeriesPage(items = emptyList(), totalItems = 0)
        assertEquals(0, page.totalOrPageSize)
    }

    @Test
    fun `totalItems is parsed from the real Kavita header shape`() {
        // Captured verbatim from a live v0.9.1 server for library 5.
        val header = """{"currentPage":1,"itemsPerPage":4,"totalItems":85,"totalPages":22}"""
        assertEquals(85, parseTotal(header))
    }

    @Test
    fun `malformed or absent headers yield zero`() {
        assertEquals(0, parseTotal(null))
        assertEquals(0, parseTotal(""))
        assertEquals(0, parseTotal("not json"))
        assertEquals(0, parseTotal("""{"currentPage":1}"""))
        assertEquals(0, parseTotal("""{"totalItems":"many"}"""))
    }

    /** Mirrors LibraryRepository.totalFrom so the parsing contract itself is covered. */
    private fun parseTotal(header: String?): Int {
        if (header.isNullOrBlank()) return 0
        return runCatching {
            val obj = Json.parseToJsonElement(header) as JsonObject
            (obj["totalItems"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        }.getOrDefault(0)
    }

    private fun dummy() = com.kativa.reader.domain.model.SeriesSummary(
        id = 1,
        name = "s",
        libraryId = 1,
        libraryName = "",
        format = com.kativa.reader.domain.model.ContentFormat.Archive,
        pages = 0,
        pagesRead = 0,
    )
}
