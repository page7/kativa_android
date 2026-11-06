package com.kativa.reader.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fraction mapping for [KativaSlider].
 *
 * The reader progress bar maps a page index to a 0..1 position; a zero-length range (single-page
 * chapter) used to divide by zero and render a full or NaN track.
 */
class KativaSliderFractionTest {

    @Test
    fun `start of range maps to zero`() {
        assertEquals(0f, fractionOf(0f, 0f..179f), 0.0001f)
    }

    @Test
    fun `end of range maps to one`() {
        assertEquals(1f, fractionOf(179f, 0f..179f), 0.0001f)
    }

    @Test
    fun `midpoint maps to half`() {
        assertEquals(0.5f, fractionOf(0.5f, 0f..1f), 0.0001f)
    }

    @Test
    fun `zero length range does not divide by zero`() {
        assertEquals(0f, fractionOf(0f, 0f..0f), 0.0001f)
        assertEquals(0f, fractionOf(5f, 3f..3f), 0.0001f)
    }

    @Test
    fun `values outside the range are clamped`() {
        assertEquals(0f, fractionOf(-10f, 0f..179f), 0.0001f)
        assertEquals(1f, fractionOf(500f, 0f..179f), 0.0001f)
    }

    @Test
    fun `offset range is honoured`() {
        // Brightness slider: 0.2..1.0.
        assertEquals(0f, fractionOf(0.2f, 0.2f..1f), 0.0001f)
        assertEquals(1f, fractionOf(1f, 0.2f..1f), 0.0001f)
        assertEquals(0.5f, fractionOf(0.6f, 0.2f..1f), 0.0001f)
    }
}
