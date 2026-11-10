package com.kavita.reader.ui.series

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source guard for two series-detail hero contracts that compile fine and silently regress.
 *
 * 1. **Actions sit on the cover's bottom edge.** The hero is a `Row` of poster + metadata column.
 *    When the metadata column is shorter than the 120dp×2:3 poster, a plain `Column` leaves the
 *    ＂阅读 / 想读＂ row floating mid-cover. The column must be at least as tall as the poster and
 *    distribute the slack with `SpaceBetween`; this cannot be asserted from a JVM unit test, so it
 *    is pinned in the source the way [ThemeContractTest] pins the launch theme.
 *
 * 2. **Long-press copies the title** — and a *click* must stay inert, otherwise the title acquires
 *    a semantics "click" action that does nothing when a TalkBack user activates it.
 */
class SeriesHeroContractTest {

    private val moduleRoot = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")

    private val source: String by lazy {
        File(moduleRoot, "java/com/kavita/reader/ui/series/SeriesDetailScreen.kt")
            .also { assertTrue("Expected ${it.absolutePath}", it.isFile) }
            .readText()
            .replace("\r\n", "\n")
    }

    @Test
    fun `hero action row is pinned to the poster bottom`() {
        assertTrue(
            "The metadata column must reserve the poster height (heightIn(min = HeroCoverHeight)) " +
                "or the action row floats mid-cover when the text is short.",
            source.contains("heightIn(min = HeroCoverHeight)"),
        )
        assertTrue(
            "The metadata column must distribute the slack with SpaceBetween so the actions land " +
                "on the poster's bottom edge.",
            source.contains("verticalArrangement = Arrangement.SpaceBetween"),
        )
        assertTrue(
            "HeroCoverHeight must be derived from KavitaDimens.PosterAspect so cover and action " +
                "row cannot drift apart.",
            Regex("""HeroCoverHeight = HeroCoverWidth / KavitaDimens\.PosterAspect""")
                .containsMatchIn(source),
        )
    }

    @Test
    fun `title long-press copies without adding a click action`() {
        assertTrue(
            "The title must handle a long press",
            source.contains("detectTapGestures(") && source.contains("onLongPress ="),
        )
        assertTrue(
            "The title must copy the series name",
            source.contains("onCopyTitle"),
        )
        assertTrue(
            "A no-op `combinedClickable(onClick = {})` would advertise a click action to TalkBack; " +
                "the title must not use it.",
            !source.contains("onClick = {},"),
        )
        assertTrue(
            "TalkBack needs an equivalent entry point for the copy action",
            source.contains("CustomAccessibilityAction("),
        )
    }
}
