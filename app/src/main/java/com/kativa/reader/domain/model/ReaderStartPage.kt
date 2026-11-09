package com.kativa.reader.domain.model

/**
 * Where a reader session should open.
 *
 * Two independent sources can say where to start:
 *  - the server's saved progress (`startPageIndex`), the normal case;
 *  - an explicit page carried by a bookmark link.
 *
 * The explicit page wins — the user tapped *that* page — but it still has to be clamped, because
 * a bookmark can outlive the chapter it belongs to (pages were re-scanned, the archive changed)
 * and an out-of-range index would crash the pager.
 *
 * Kept as a pure object so the off-by-one rules are unit-testable without a ViewModel; the values
 * are **0-based indexes**, matching `Reader/image?page=` and `BookmarkDto.page`.
 */
object ReaderStartPage {

    fun resolve(
        explicitPageIndex: Int?,
        totalPages: Int,
        progressPageIndex: Int,
    ): Int {
        val lastIndex = (totalPages - 1).coerceAtLeast(0)
        val progress = progressPageIndex.coerceIn(0, lastIndex)
        if (explicitPageIndex == null) return progress
        return explicitPageIndex.coerceIn(0, lastIndex)
    }
}
