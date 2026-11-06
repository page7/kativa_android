package com.kativa.reader.ui.reader

import com.kativa.reader.domain.model.PageLayout
import com.kativa.reader.domain.model.ReaderMode
import com.kativa.reader.domain.model.ReadingDirection
import com.kativa.reader.domain.model.ScalingOption

/**
 * Reader settings, resolved from the server reading profile when available and otherwise from
 * these defaults. Kept small and serialisable-free: persistence is the caller's problem.
 *
 * Mirrors `UserReadingProfileDto` fields the mobile reader actually honours
 * (docs/API.md §4). Unsupported options are deliberately absent rather than faked.
 */
data class ReaderSettings(
    val readingDirection: ReadingDirection = ReadingDirection.LeftToRight,
    val readerMode: ReaderMode = ReaderMode.LeftRight,
    val layoutMode: PageLayout = PageLayout.Single,
    val scalingOption: ScalingOption = ScalingOption.FitToHeight,
    /** Skip pairing page 1 so the rest of the chapter spreads evenly. */
    val coverOffset: Boolean = true,
    /** Background behind pages; black is the safe default for comics. */
    val backgroundColor: String = "#000000",
    /** In-session brightness multiplier, 0.2..1.0. Never persisted to the server. */
    val brightness: Float = 1f,
    /** Show a faint page number in the corner. */
    val showPageNumber: Boolean = true,
    /** Let the volume keys turn pages instead of changing media volume. Off unless opted in. */
    val volumeKeyPaging: Boolean = false,
) {
    val isWebtoon: Boolean get() = readerMode == ReaderMode.Webtoon

    val isVerticalPaging: Boolean get() = readerMode == ReaderMode.UpDown

    val isDoublePage: Boolean get() = !isWebtoon && layoutMode != PageLayout.Single

    /** RTL affects tap zones / pager order, not slot construction. */
    val isRightToLeft: Boolean get() = readingDirection == ReadingDirection.RightToLeft

    companion object {
        val Default = ReaderSettings()
    }
}
