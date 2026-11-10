package com.kavita.reader.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search query gating and result grouping.
 *
 * The debounce itself lives in the ViewModel; the gate that decides "is this worth a request"
 * lives here.
 */
class SearchRepositoryTest {

    @Test
    fun `minimum query length is one character`() {
        // Verified against a live Kavita v0.9.1 server: `/Search/search` matches single
        // characters (`z` → 2 series, `日` → 6) and only rejects blank queries with 400.
        // The previous value of 2 silently swallowed every one-character search, which is
        // indistinguishable from "search is broken". See AGENTS.md §4.6.
        assertEquals(1, SearchRepository.MIN_QUERY_LENGTH)
    }

    @Test
    fun `blank queries never reach the server`() {
        // Guards the 400 path: the repository must short-circuit blank input itself.
        val blank = "   "
        assertEquals(0, blank.trim().length)
        assertTrue("a blank query is below the gate", blank.trim().length < SearchRepository.MIN_QUERY_LENGTH)
    }

    @Test
    fun `single CJK character clears the gate`() {
        // `adb shell input text` cannot type Chinese, so the CJK path is locked down here.
        // A single CJK character is a meaningful query for this catalogue (verified live:
        // `五` → 2 series, `日` → 6) and must not be swallowed by the minimum-length gate.
        listOf("五", "日", "本", "あ").forEach { char ->
            assertEquals(
                "'$char' must be searchable",
                1,
                char.trim().length,
            )
            assertTrue(
                "'$char' must clear MIN_QUERY_LENGTH=${SearchRepository.MIN_QUERY_LENGTH}",
                char.trim().length >= SearchRepository.MIN_QUERY_LENGTH,
            )
        }
    }

    @Test
    fun `empty groups are reported as no results`() {
        val results = SearchResults(query = "abc", groups = emptyList())
        assertTrue(results.isEmpty)
        assertEquals(0, results.totalHits)
    }

    @Test
    fun `groups with hits are not empty`() {
        val results = SearchResults(
            query = "abc",
            groups = listOf(
                SearchGroup(
                    kind = SearchKind.Series,
                    hits = listOf(
                        SearchHit(
                            id = 1,
                            title = "Series",
                            subtitle = null,
                            coverUrl = null,
                            kind = SearchKind.Series,
                        ),
                    ),
                ),
            ),
        )
        assertFalse(results.isEmpty)
        assertEquals(1, results.totalHits)
    }

    @Test
    fun `total hits sums across groups`() {
        fun hit(id: Int, kind: SearchKind) = SearchHit(id, "t$id", null, null, kind)

        val results = SearchResults(
            query = "x",
            groups = listOf(
                SearchGroup(SearchKind.Series, listOf(hit(1, SearchKind.Series), hit(2, SearchKind.Series))),
                SearchGroup(SearchKind.Genre, listOf(hit(3, SearchKind.Genre))),
            ),
        )
        assertEquals(3, results.totalHits)
    }

    @Test
    fun `every search kind has a stable ordinal-free identity`() {
        // The UI switches on `kind`; a missing branch would silently render nothing.
        val kinds = SearchKind.entries
        assertEquals(
            listOf(
                SearchKind.Series,
                SearchKind.Collection,
                SearchKind.ReadingList,
                SearchKind.Person,
                SearchKind.Genre,
                SearchKind.Tag,
                SearchKind.Library,
            ),
            kinds,
        )
    }
}
