package com.kativa.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reader's brightness slider dims the page content with a scrim (see [ReaderDim]).
 *
 * These bounds are what keep the control usable: at 100% no scrim must be drawn at all (a 0.01
 * veil over artwork is visible on an OLED panel), and at the 20% floor the page must still be
 * readable rather than black.
 */
class ReaderDimTest {

    @Test
    fun `full brightness draws no scrim`() {
        assertEquals(0f, ReaderDim.alphaFor(1f), 0.0001f)
    }

    @Test
    fun `lowest brightness is dim but not black`() {
        val alpha = ReaderDim.alphaFor(0.2f)
        assertTrue("expected a visible scrim, got $alpha", alpha > 0.5f)
        assertTrue("page must stay readable, got $alpha", alpha < 0.8f)
    }

    @Test
    fun `brightness below the floor is clamped to the floor`() {
        assertEquals(ReaderDim.alphaFor(0.2f), ReaderDim.alphaFor(0f), 0.0001f)
        assertEquals(ReaderDim.alphaFor(0.2f), ReaderDim.alphaFor(-5f), 0.0001f)
    }

    @Test
    fun `brightness above one never produces a negative scrim`() {
        assertEquals(0f, ReaderDim.alphaFor(3f), 0.0001f)
    }

    @Test
    fun `scrim grows monotonically as brightness drops`() {
        var previous = -1f
        var value = 1f
        while (value >= 0.2f) {
            val alpha = ReaderDim.alphaFor(value)
            assertTrue("alpha must not shrink as brightness drops ($value -> $alpha)", alpha >= previous)
            previous = alpha
            value -= 0.05f
        }
    }
}
