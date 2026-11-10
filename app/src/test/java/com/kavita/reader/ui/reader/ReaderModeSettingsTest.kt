package com.kavita.reader.ui.reader

import com.kavita.reader.domain.model.ReaderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard for the "stuck in Webtoon" regression.
 *
 * The reader screen used to `return@Box` from inside its Webtoon branch. The settings sheet is
 * composed further down in the same `Box` lambda, so that early return skipped it: once the user
 * picked continuous scrolling, the settings drawer could never be opened again and there was no
 * way back to any other paging mode.
 *
 * The `return@Box` itself cannot be asserted from a JVM test, but the two decisions that surround
 * it can: which mode renders which reader, and that the settings sheet is *not* conditional on the
 * paging mode. [ReaderChrome.showsSettingsSheet] is the predicate the screen uses, so a future
 * refactor that reintroduces a mode-dependent guard fails here.
 */
class ReaderModeSettingsTest {

    @Test
    fun `settings sheet is reachable in every paging mode`() {
        ReaderMode.entries.forEach { mode ->
            assertTrue(
                "settings must be reachable in $mode",
                ReaderChrome.showsSettingsSheet(sheetVisible = true, readerMode = mode),
            )
        }
    }

    @Test
    fun `a closed sheet stays closed in every paging mode`() {
        ReaderMode.entries.forEach { mode ->
            assertFalse(ReaderChrome.showsSettingsSheet(sheetVisible = false, readerMode = mode))
        }
    }

    @Test
    fun `webtoon is the only mode that uses continuous scroll`() {
        assertTrue(ReaderChrome.usesContinuousScroll(ReaderMode.Webtoon))
        assertFalse(ReaderChrome.usesContinuousScroll(ReaderMode.LeftRight))
        assertFalse(ReaderChrome.usesContinuousScroll(ReaderMode.UpDown))
    }

    @Test
    fun `continuous scroll start index is clamped to the chapter`() {
        // Switching modes must carry the reading position over instead of rewinding to page 1.
        assertEquals(174, ReaderChrome.webtoonStartIndex(pageIndex = 174, totalPages = 237))
        assertEquals(0, ReaderChrome.webtoonStartIndex(pageIndex = -5, totalPages = 237))
        assertEquals(236, ReaderChrome.webtoonStartIndex(pageIndex = 999, totalPages = 237))
        assertEquals(0, ReaderChrome.webtoonStartIndex(pageIndex = 3, totalPages = 0))
    }
}
