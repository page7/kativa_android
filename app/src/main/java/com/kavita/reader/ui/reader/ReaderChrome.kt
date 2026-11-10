package com.kavita.reader.ui.reader

import com.kavita.reader.domain.model.ReaderMode

/**
 * Pure predicates behind the reader's mode branching (AGENTS.md §5).
 *
 * Kept out of the composable so the rules can be unit-tested on the JVM. The important one is
 * [showsSettingsSheet]: the sheet must be reachable regardless of paging mode. The reader once
 * `return@Box`-ed early for Webtoon, which silently skipped the sheet and trapped the user in
 * continuous scrolling with no way back (see `ReaderModeSettingsTest`).
 */
object ReaderChrome {

    /** Whether the settings drawer should be composed. Deliberately independent of paging mode. */
    fun showsSettingsSheet(sheetVisible: Boolean, @Suppress("UNUSED_PARAMETER") readerMode: ReaderMode): Boolean =
        sheetVisible

    /** Continuous scroll uses a `LazyColumn`, every other mode uses a pager. */
    fun usesContinuousScroll(readerMode: ReaderMode): Boolean = readerMode == ReaderMode.Webtoon

    /**
     * First visible item to hand to the Webtoon list so the reading position survives a mode
     * switch. Clamped to the chapter so an out-of-range index cannot produce an empty list.
     */
    fun webtoonStartIndex(pageIndex: Int, totalPages: Int): Int {
        if (totalPages <= 0) return 0
        return pageIndex.coerceIn(0, totalPages - 1)
    }
}
