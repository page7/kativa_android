package com.kavita.reader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Double-page pairing. The failure modes here are visible and ugly (a spread split across two
 * screens, or a wide page paired with its neighbour), so every branch is pinned.
 */
class PagePairingTest {

    @Test
    fun `cover offset shows page one alone then pairs the rest`() {
        val slots = PagePairing.doubleSlots(totalPages = 6, widePages = emptySet(), startOffset = 0)

        assertEquals(
            listOf(
                PageSlot(1, null),
                PageSlot(2, 3),
                PageSlot(4, 5),
                PageSlot(6, null),
            ),
            slots,
        )
    }

    @Test
    fun `without cover offset pairing starts immediately`() {
        val slots = PagePairing.doubleSlots(totalPages = 5, widePages = emptySet(), startOffset = 1)

        assertEquals(
            listOf(
                PageSlot(1, 2),
                PageSlot(3, 4),
                PageSlot(5, null),
            ),
            slots,
        )
    }

    @Test
    fun `a wide page never gets paired`() {
        // Page 3 is a double spread; it must stand alone and page 4 must start a new slot.
        val slots = PagePairing.doubleSlots(
            totalPages = 6,
            widePages = setOf(3),
            startOffset = 1,
        )

        assertEquals(
            listOf(
                PageSlot(1, 2),
                PageSlot(3, null),
                PageSlot(4, 5),
                PageSlot(6, null),
            ),
            slots,
        )
    }

    @Test
    fun `wide page as the second half pushes it to the next slot`() {
        // Page 2 is wide, so page 1 stands alone and page 2 is not absorbed into its slot.
        val slots = PagePairing.doubleSlots(
            totalPages = 4,
            widePages = setOf(2),
            startOffset = 1,
        )

        assertEquals(
            listOf(
                PageSlot(1, null),
                PageSlot(2, null),
                PageSlot(3, 4),
            ),
            slots,
        )
    }

    @Test
    fun `every page appears exactly once`() {
        val total = 17
        val slots = PagePairing.doubleSlots(total, widePages = setOf(4, 9), startOffset = 0)
        val pages = slots.flatMap { listOfNotNull(it.first, it.second) }.sorted()

        assertEquals((1..total).toList(), pages)
    }

    @Test
    fun `single page chapter still yields one slot`() {
        val slots = PagePairing.doubleSlots(totalPages = 1, widePages = emptySet(), startOffset = 0)
        assertEquals(listOf(PageSlot(1, null)), slots)
    }

    @Test
    fun `empty chapter yields no slots`() {
        assertTrue(PagePairing.doubleSlots(0, emptySet()).isEmpty())
    }

    @Test
    fun `slot lookup finds the slot containing a page`() {
        val slots = PagePairing.doubleSlots(totalPages = 6, widePages = emptySet(), startOffset = 0)

        assertEquals(0, PagePairing.slotIndexOf(slots, 1))
        assertEquals(1, PagePairing.slotIndexOf(slots, 2))
        assertEquals(1, PagePairing.slotIndexOf(slots, 3))
        assertEquals(3, PagePairing.slotIndexOf(slots, 6))
        assertNull(PagePairing.slotIndexOf(slots, 99))
    }

    @Test
    fun `leading page is the first page of the slot`() {
        val slots = PagePairing.doubleSlots(totalPages = 6, widePages = emptySet(), startOffset = 0)

        // Page 3 is the right half of slot 1, whose leading page is 2 — that is what we persist.
        assertEquals(2, PagePairing.leadingPageOf(slots, 3))
        assertEquals(1, PagePairing.leadingPageOf(slots, 1))
    }

    @Test
    fun `slot page count reflects pairing`() {
        assertEquals(1, PageSlot(5, null).pageCount)
        assertEquals(2, PageSlot(5, 6).pageCount)
        assertTrue(PageSlot(5, 6).contains(6))
    }
}
