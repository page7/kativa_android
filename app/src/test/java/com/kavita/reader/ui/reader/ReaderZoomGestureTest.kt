package com.kavita.reader.ui.reader

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source guard for the "zoom only works on the first page" regression.
 *
 * [rememberReaderZoomState] is **keyed by the page slot**, which is how zoom resets synchronously
 * when the user turns a page. Recreating the state is not enough on its own: a `pointerInput` block
 * only rebuilds its handler when one of its keys changes, so a gesture modifier keyed on the reading
 * mode alone kept its original closure and went on calling the state object of the page the reader
 * had already left. The double tap then flipped a discarded object and the new page never
 * magnified — reproduced on device with the log line
 *
 *     PagedReader compose slot=56 state=24854220
 *     onDoubleTap        state=39548202 slot=55
 *
 * Compose runtime bugs of this shape are invisible to JVM unit tests, so — like
 * [com.kavita.reader.core.designsystem.PrivacyPlaceholderSizingTest] — the contract is pinned in the
 * source: every `pointerInput` that can see a [ReaderZoomState] must also take it as a key.
 */
class ReaderZoomGestureTest {

    private val moduleRoot = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")

    private fun source(relative: String): String {
        val file = File(moduleRoot, relative)
        assertTrue("Expected file at ${file.absolutePath}", file.isFile)
        return file.readText().replace("\r\n", "\n")
    }

    /**
     * Keys passed to the `pointerInput` that starts at [start], i.e. the text between
     * `pointerInput(` and the matching `)`.
     *
     * Checking the whole surrounding block instead would pass even with the key removed, because
     * `zoomState` also appears in the body (`zoomState.toggleDoubleTap(...)`) — a guard that cannot
     * fail is worse than no guard.
     */
    private fun pointerInputKeys(text: String, from: Int): String {
        val open = text.indexOf("pointerInput(", from)
        assertTrue("expected a pointerInput(...) call after index $from", open >= 0)
        val argsStart = open + "pointerInput(".length
        var depth = 1
        var i = argsStart
        while (i < text.length && depth > 0) {
            when (text[i]) {
                '(' -> depth++
                ')' -> depth--
            }
            i++
        }
        assertTrue("unbalanced pointerInput( arguments", depth == 0)
        return text.substring(argsStart, i - 1)
    }

    @Test
    fun `pager gesture modifier keys on the zoom state it captures`() {
        val text = source("java/com/kavita/reader/ui/reader/MangaReaderScreen.kt")
        val start = text.indexOf("val pagerModifier")
        assertTrue("expected a pagerModifier block", start >= 0)

        val keys = pointerInputKeys(text, start)
        assertTrue(
            "The pager's `pointerInput` captures `zoomState`, so it must be a *key*: otherwise the\n" +
                "handler survives a page turn and keeps calling the previous page's discarded state,\n" +
                "which makes double-tap zoom stop working after paging.\n" +
                "Keys were: $keys",
            keys.contains("zoomState"),
        )
    }

    @Test
    fun `webtoon gesture node keys on the zoom state it captures`() {
        val text = source("java/com/kavita/reader/ui/reader/MangaReaderScreen.kt")
        val start = text.indexOf("private fun WebtoonReader(")
        assertTrue("expected WebtoonReader", start >= 0)

        val keys = pointerInputKeys(text, start)
        assertTrue(
            "Webtoon's tap node captures `zoomState`, so it must be a key too; keying only on " +
                "`zoomed` would pin the handler to the first state instance.\nKeys were: $keys",
            keys.contains("zoomState"),
        )
    }

    @Test
    fun `rememberReaderZoomState is keyed so a page turn rebuilds the state`() {
        val text = source("java/com/kavita/reader/ui/reader/MangaReaderScreen.kt")
        assertTrue(
            "Zoom must reset per page: `rememberReaderZoomState` has to receive the reading keys " +
                "(currentSlot / mode / layout), not be called with none.",
            Regex("""rememberReaderZoomState\(\s*state\.currentSlot""").containsMatchIn(text),
        )
    }

    /**
     * The state itself must never infer "am I zoomed?" from the animating `scale`: that is the bug
     * that made zoom enterable only once (see [ReaderZoomState] and `ReaderZoomTest`).
     */
    @Test
    fun `isZoomed follows the synchronous intent, not the animating scale`() {
        val text = source("java/com/kavita/reader/ui/reader/ReaderZoom.kt")
        assertTrue(
            "isZoomed must read `targetScale`; deriving it from `scale` makes the flag lag the " +
                "user's intent by one animation.",
            text.contains("val isZoomed: Boolean get() = ReaderZoom.isZoomed(targetScale)"),
        )
    }
}
