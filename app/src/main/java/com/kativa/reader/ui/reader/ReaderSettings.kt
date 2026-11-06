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
 * (docs/API.md §4). Unsupported options are deliberately absent rather than faked: every field
 * here must be read by the renderer, otherwise it is a dead control (see `ReaderSettingsContract`).
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

    /**
     * A facing pair only exists in horizontal paging. Vertical paging stacks one page per screen
     * and Webtoon is a continuous strip, so neither can render a spread — pairing pages there
     * used to stack two pages inside a single vertical slot.
     */
    val isDoublePage: Boolean get() = readerMode == ReaderMode.LeftRight && layoutMode != PageLayout.Single

    /** RTL affects tap zones / pager order, not slot construction. */
    val isRightToLeft: Boolean get() = readingDirection == ReadingDirection.RightToLeft

    /**
     * True when the reading direction actually changes anything.
     *
     * Direction is a *horizontal* concept: it mirrors tap zones, the pager order and the order of
     * a facing pair. Vertical paging and Webtoon scroll top-to-bottom regardless, so showing the
     * control there would be a dead switch. The settings sheet hides it in those modes.
     */
    val readingDirectionApplies: Boolean get() = readerMode == ReaderMode.LeftRight

    /**
     * Page layout only means something when pages are paired side by side. Webtoon is a single
     * continuous strip, and vertical paging stacks one page per screen; a "double page" there
     * would either be meaningless or put two pages on top of each other.
     */
    val pageLayoutApplies: Boolean get() = readerMode == ReaderMode.LeftRight

    companion object {
        val Default = ReaderSettings()
    }
}

