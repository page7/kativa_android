package com.kativa.reader.domain.model

/**
 * Page pairing for double-page reading.
 *
 * Kavita sends `doublePairs` as a page→page map (1-based) describing which spreads are wide.
 * When the map is absent we fall back to the per-page `isWide` flags from `pageDimensions`.
 *
 * Pure logic so it can be unit-tested without Compose or a server (AGENTS.md §8).
 */
object PagePairing {

    /**
     * Builds the render slots for double-page mode.
     *
     * Rules that matter in practice (AGENTS.md §5):
     *  - a wide page occupies a slot alone (it must not be paired with its neighbour);
     *  - a page already consumed as the right half of a pair is not emitted again;
     *  - reading direction does not change the *slots*, only how they are laid out on screen.
     *
     * @param totalPages number of pages (1-based count)
     * @param widePages pages (1-based) that should never be paired
     * @param startOffset 0 = start covering from page 1 (cover alone), 1 = start pairing immediately
     */
    fun doubleSlots(
        totalPages: Int,
        widePages: Set<Int>,
        startOffset: Int = 0,
    ): List<PageSlot> {
        if (totalPages <= 0) return emptyList()

        val slots = mutableListOf<PageSlot>()
        var page = 1

        // With a cover offset, page 1 is shown alone so the rest pairs evenly.
        if (startOffset == 0) {
            slots += PageSlot(first = 1, second = null)
            page = 2
        }

        while (page <= totalPages) {
            if (page in widePages) {
                slots += PageSlot(first = page, second = null)
                page += 1
                continue
            }

            val next = page + 1
            if (next <= totalPages && next !in widePages) {
                slots += PageSlot(first = page, second = next)
                page += 2
            } else {
                slots += PageSlot(first = page, second = null)
                page += 1
            }
        }

        return slots
    }

    /** Finds the slot that contains [page] (1-based). Returns null when out of range. */
    fun slotIndexOf(slots: List<PageSlot>, page: Int): Int? =
        slots.indexOfFirst { it.contains(page) }.takeIf { it >= 0 }

    /** The first page of the slot containing [page]; used when persisting progress. */
    fun leadingPageOf(slots: List<PageSlot>, page: Int): Int =
        slots.getOrNull(slotIndexOf(slots, page) ?: -1)?.first ?: page
}

/** One rendered unit in double-page mode: either a single page or a facing pair. */
data class PageSlot(
    val first: Int,
    val second: Int? = null,
) {
    fun contains(page: Int): Boolean = page == first || page == second

    val pageCount: Int get() = if (second == null) 1 else 2
}
