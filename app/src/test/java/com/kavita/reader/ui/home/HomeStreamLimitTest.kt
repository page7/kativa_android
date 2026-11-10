package com.kavita.reader.ui.home

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the "each home stream shows at most ten entries" product rule.
 *
 * The limit has to be enforced in two places and both matter:
 *  - the request: `pageSize` is what the server actually honours, so the row never transfers a
 *    page it is about to discard (Kavita defaults `pageSize` to `int.MaxValue`, see AGENTS.md §4.3);
 *  - the mapping: `take` guards against a server that ignores the parameter, which would
 *    otherwise render an unbounded row.
 *
 * This is a source guard because both are plain strings and numbers on a ViewModel that needs
 * Retrofit + Hilt to instantiate; see [ThemeContractTest] for the precedent.
 */
class HomeStreamLimitTest {

    private val moduleRoot = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")

    private val homeViewModel: String by lazy {
        File(moduleRoot, "java/com/kavita/reader/ui/home/HomeViewModel.kt")
            .also { assertTrue("Expected ${it.absolutePath}", it.isFile) }
            .readText()
            .replace("\r\n", "\n")
    }

    @Test
    fun `the stream page size is ten`() {
        val match = Regex("""const val STREAM_PAGE_SIZE = (\d+)""").find(homeViewModel)
        assertTrue("expected a STREAM_PAGE_SIZE constant in HomeViewModel", match != null)
        assertEquals(10, match!!.groupValues[1].toInt())
    }

    @Test
    fun `all three streams request the ten-item page`() {
        listOf("onDeck(", "recentlyUpdated(", "recentlyAdded(").forEach { call ->
            val index = homeViewModel.indexOf(call)
            assertTrue("expected a $call call in HomeViewModel", index >= 0)
            val args = homeViewModel.substring(index, minOf(index + 120, homeViewModel.length))
            assertTrue(
                "$call must ask the server for STREAM_PAGE_SIZE entries; found: $args",
                args.contains("pageSize = STREAM_PAGE_SIZE"),
            )
        }
    }

    @Test
    fun `each stream is trimmed to ten even if the server over-returns`() {
        // One `take(STREAM_PAGE_SIZE)` per stream mapping.
        val takes = Regex("""take\(STREAM_PAGE_SIZE\)""").findAll(homeViewModel).count()
        assertTrue(
            "Expected one take(STREAM_PAGE_SIZE) per stream (3), found $takes. A server that " +
                "ignores pageSize must not be able to render an endless home row.",
            takes >= 3,
        )
    }

    @Test
    fun `the old twenty-item page size is gone`() {
        assertTrue(
            "HomeViewModel must not keep hard-coded pageSize = 20 alongside STREAM_PAGE_SIZE",
            !homeViewModel.contains("pageSize = 20"),
        )
    }
}
