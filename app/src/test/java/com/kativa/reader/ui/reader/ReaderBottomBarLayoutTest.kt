package com.kativa.reader.ui.reader

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layout guard for the reader's bottom bar.
 *
 * The bar used to spend height in two separate ways, and both are invisible to normal tests
 * because they are pure layout, not logic:
 *
 *  1. the page counter shared a `Row` with the prev/next chapter `IconButton`s, so the single line
 *     of text inherited their 48dp minimum touch target;
 *  2. `KativaSlider` stacked a drag bubble in its own row above the track, reserving another 22dp
 *     even when nothing was being dragged.
 *
 * Together they left ~50dp of dead space between the counter and the 4dp track. Measured on the
 * 1080x2400 emulator at 440dpi: counter `y=2034..2073`, slider node `y=2224..2268`. After the fix
 * (counter on its own line, bubble dropped in favour of live counter text) the same screen measures
 * counter `y=2151..2190`, slider node `y=2240..2284` — the gap above the track shrank from 151px
 * to 50px and the whole bar moved down out of the artwork.
 *
 * These are source contracts, the same technique [com.kativa.reader.ThemeContractTest] uses, since
 * Compose layout regressions do not fail any assertion on the JVM.
 */
class ReaderBottomBarLayoutTest {

    private val moduleRoot = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")

    private fun source(relativePath: String): String =
        File(moduleRoot, relativePath).readText().replace("\r\n", "\n")

    private fun bodyOf(relativePath: String, function: String): String {
        val text = source(relativePath)
        val start = text.indexOf("fun $function(")
        assertTrue("Expected `fun $function(` in $relativePath", start >= 0)
        val end = text.indexOf("\n}", start)
        assertTrue("Could not find the end of $function in $relativePath", end > start)
        return text.substring(start, end)
    }

    @Test
    fun `page counter is not in a row with the chapter icon buttons`() {
        val body = bodyOf("java/com/kativa/reader/ui/reader/MangaReaderScreen.kt", "ReaderControls")
        assertFalse(
            "The page counter must not share a Row with IconButtons: it then inherits their 48dp " +
                "minimum height and pushes the progress track far below the text",
            body.contains("Row(verticalAlignment = Alignment.CenterVertically) {\n                    IconButton") &&
                body.contains("stringResource(R.string.reader_page, state.displayPage"),
        )
    }

    @Test
    fun `counter line sits above the chapter row`() {
        val body = bodyOf("java/com/kativa/reader/ui/reader/MangaReaderScreen.kt", "ReaderControls")
        val counter = body.indexOf("stringResource(")
        val row = body.indexOf("Row(verticalAlignment = Alignment.CenterVertically)")
        assertTrue("Expected both a counter text and a chapter row", counter >= 0 && row >= 0)
        assertTrue(
            "The page counter must be declared before the slider/chapter row, on its own line",
            counter < row,
        )
    }

    @Test
    fun `slider reserves no drag bubble row`() {
        val file = "java/com/kativa/reader/core/designsystem/KativaSlider.kt"
        val text = source(file)
        assertFalse(
            "KativaSlider must not stack a bubble above the track: that reserved ~22dp of layout " +
                "height even while idle and opened the gap under the page counter",
            text.contains("BubbleSlotHeight") || text.contains("ValueBubble"),
        )
        assertFalse(
            "KativaSlider must not wrap the Slider in a Column; any extra row above it becomes " +
                "permanent bottom-bar height",
            text.contains("Column("),
        )
    }

    @Test
    fun `dragging reports the target page through the counter`() {
        val body = bodyOf("java/com/kativa/reader/ui/reader/MangaReaderScreen.kt", "ReaderControls")
        assertTrue(
            "With the bubble gone the counter must surface the dragged page, otherwise the user " +
                "loses the seek preview entirely",
            body.contains("dragValue?.roundToInt()?.plus(1)"),
        )
    }
}
