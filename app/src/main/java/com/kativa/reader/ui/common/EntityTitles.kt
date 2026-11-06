package com.kativa.reader.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.kativa.reader.R
import com.kativa.reader.domain.model.Chapter
import com.kativa.reader.domain.model.Volume

/**
 * The single place that turns Kavita volume/chapter entities into display strings.
 *
 * Kavita encodes two internal sentinels that must never reach the screen
 * (see [com.kativa.reader.domain.model.KavitaNumbering]):
 *  - `-100000` = no number was parsed
 *  - `100000`  = the synthetic volume holding specials
 *
 * Subtlety that caused a real bug: the server pre-formats `ChapterDto.Title` into strings like
 * `"Chapter -100000"`, so a "has a title" check is not enough — the title itself must be scanned
 * for sentinels. Hence [Chapter.safeTitle].
 *
 * "全一章" (single/full chapter) semantics:
 *  - a *loose-leaf* volume with exactly one chapter is "全一章" (one file that makes up the whole
 *    volume, nothing else to read);
 *  - inside a normal volume, a chapter without its own number is "单话" (it is one entry among
 *    siblings, so calling it "全一章" would be misleading).
 */
object EntityTitles {

    @Composable
    @ReadOnlyComposable
    fun chapter(chapter: Chapter): String = chapter(chapter, isOnlyChapterOfVolume = false)

    /**
     * @param isOnlyChapterOfVolume true when this chapter is the only entry in a synthetic
     *   (loose-leaf) volume, which makes it "全一章".
     */
    @Composable
    @ReadOnlyComposable
    fun chapter(chapter: Chapter, isOnlyChapterOfVolume: Boolean): String {
        chapter.safeTitle?.let { return it }
        chapter.displayNumber?.let { return stringResource(R.string.entity_chapter_num, it) }
        return if (chapter.isUnnumbered && isOnlyChapterOfVolume) {
            stringResource(R.string.entity_full_chapter)
        } else {
            stringResource(R.string.entity_unnumbered_chapter)
        }
    }

    @Composable
    @ReadOnlyComposable
    fun volume(volume: Volume): String {
        volume.safeName?.let { return it }
        return when {
            volume.isLooseLeaf -> stringResource(R.string.entity_loose_leaf)
            volume.isSpecials -> stringResource(R.string.entity_specials)
            else -> stringResource(R.string.entity_volume_num, formatNumber(volume.minNumber))
        }
    }

    /** Compact label used inside the reader overlay, where only the session is available. */
    @Composable
    @ReadOnlyComposable
    fun chapterCompact(session: com.kativa.reader.domain.model.ReaderSession): String {
        val title = session.chapterTitle
        if (title.isNotBlank() &&
            !title.startsWith("-") &&
            !com.kativa.reader.domain.model.KavitaNumbering.containsSentinel(title)
        ) {
            return title
        }

        val number = session.chapterNumber
        if (number.isNotBlank() &&
            !number.startsWith("-") &&
            number.toFloatOrNull() != com.kativa.reader.domain.model.KavitaNumbering.UNNUMBERED
        ) {
            val parsed = number.toFloatOrNull()
            val rendered = if (parsed != null) formatNumber(parsed) else number
            return stringResource(R.string.entity_chapter_num, rendered)
        }

        return stringResource(R.string.entity_unnumbered_chapter)
    }

    /** Formats a float number without a trailing `.0` (Kavita stores floats such as 12.5). */
    fun formatNumber(value: Float): String =
        if (value % 1f == 0f) value.toLong().toString() else value.toString()
}
