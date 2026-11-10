package com.kavita.reader.ui.reader

import androidx.compose.ui.layout.ContentScale
import com.kavita.reader.domain.model.PageLayout
import com.kavita.reader.domain.model.ReaderMode
import com.kavita.reader.domain.model.ReadingDirection
import com.kavita.reader.domain.model.ScalingOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings that must actually change what is rendered.
 *
 * The reader shipped three dead controls in a row — brightness, page-number marker and image
 * scaling — each of which stored a value and was never read. These tests pin the rules that decide
 * whether a control applies at all, and the one mapping that turns a stored option into a render
 * value, so the same class of bug fails fast.
 */
class ReaderSettingsRulesTest {

    // --- image scaling (was a dead control) ---------------------------------

    @Test
    fun `fit to height fits the whole page`() {
        assertEquals(ContentScale.Fit, ReaderScaling.contentScale(ScalingOption.FitToHeight))
    }

    @Test
    fun `fit to width fills the width`() {
        assertEquals(ContentScale.FillWidth, ReaderScaling.contentScale(ScalingOption.FitToWidth))
    }

    @Test
    fun `original draws at intrinsic size`() {
        assertEquals(ContentScale.None, ReaderScaling.contentScale(ScalingOption.Original))
    }

    @Test
    fun `automatic falls back to fit rather than cropping`() {
        // The server's "client decides" value must never be None, which would show a tiny page.
        assertEquals(ContentScale.Fit, ReaderScaling.contentScale(ScalingOption.Automatic))
    }

    @Test
    fun `every scaling option maps to a distinct intent`() {
        val mapped = ScalingOption.entries.map { ReaderScaling.contentScale(it) }
        assertTrue(
            "FillWidth and None must not collapse into the same behaviour",
            mapped.contains(ContentScale.FillWidth) && mapped.contains(ContentScale.None),
        )
    }

    @Test
    fun `fit and fill-width may coincide for wide pages but are not the same value`() {
        // A page wider than the viewport is width-bound under BOTH Fit and FillWidth, so device
        // screenshots can look identical — that is correct, not a dead control. What must never
        // happen is the two options resolving to the same ContentScale, which would make the
        // choice meaningless for tall pages.
        val fit = ReaderScaling.contentScale(ScalingOption.FitToHeight)
        val fill = ReaderScaling.contentScale(ScalingOption.FitToWidth)
        assertTrue("Fit and FillWidth must stay distinct mappings", fit != fill)
    }

    // --- reading direction only exists for horizontal paging -----------------

    @Test
    fun `reading direction applies only to horizontal paging`() {
        assertTrue(settings(ReaderMode.LeftRight).readingDirectionApplies)
        assertFalse(settings(ReaderMode.UpDown).readingDirectionApplies)
        assertFalse(settings(ReaderMode.Webtoon).readingDirectionApplies)
    }

    // --- page layout only exists for horizontal paging -----------------------

    @Test
    fun `page layout is hidden outside horizontal paging`() {
        assertTrue(settings(ReaderMode.LeftRight).pageLayoutApplies)
        assertFalse(settings(ReaderMode.UpDown).pageLayoutApplies)
        assertFalse(settings(ReaderMode.Webtoon).pageLayoutApplies)
    }

    @Test
    fun `double page never pairs pages in vertical or webtoon mode`() {
        // Regression: a double-page layout chosen in horizontal mode used to survive a switch to
        // vertical paging, stacking two pages inside one vertical slot.
        listOf(PageLayout.Double, PageLayout.DoubleReversed).forEach { layout ->
            assertFalse(
                "double page must not pair in $ReaderMode.UpDown",
                settings(ReaderMode.UpDown, layout).isDoublePage,
            )
            assertFalse(
                "double page must not pair in $ReaderMode.Webtoon",
                settings(ReaderMode.Webtoon, layout).isDoublePage,
            )
            assertTrue(
                "double page must pair in $ReaderMode.LeftRight",
                settings(ReaderMode.LeftRight, layout).isDoublePage,
            )
        }
    }

    @Test
    fun `single page layout never pairs`() {
        ReaderMode.entries.forEach { mode ->
            assertFalse(settings(mode, PageLayout.Single).isDoublePage)
        }
    }

    // --- RTL still works where it is supposed to -----------------------------

    @Test
    fun `right to left is honoured in horizontal paging`() {
        val rtl = settings(
            mode = ReaderMode.LeftRight,
            direction = ReadingDirection.RightToLeft,
        )
        assertTrue(rtl.isRightToLeft)
        assertTrue(rtl.readingDirectionApplies)
    }

    @Test
    fun `right to left is ignored where it cannot apply`() {
        // The renderer ANDs `isRightToLeft` with `readingDirectionApplies`, so a stored RTL value
        // from an earlier horizontal session must not mirror vertical or scroll reading.
        val vertical = settings(
            mode = ReaderMode.UpDown,
            direction = ReadingDirection.RightToLeft,
        )
        assertTrue("the stored preference is still RTL", vertical.isRightToLeft)
        assertFalse("but it does not apply here", vertical.readingDirectionApplies)
    }

    private fun settings(
        mode: ReaderMode,
        layout: PageLayout = PageLayout.Single,
        direction: ReadingDirection = ReadingDirection.LeftToRight,
    ) = ReaderSettings(
        readerMode = mode,
        layoutMode = layout,
        readingDirection = direction,
    )
}
