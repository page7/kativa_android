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
 * Kavita encodes "no number parsed" as `-100000` and groups specials under `100000`. Those are
 * internal sentinels and must never reach the screen (see [com.kativa.reader.domain.model.KavitaNumbering]).
 * Mirroring `UI/Web/src/app/_services/entity-title.service.ts`:
 *
 * | case | render |
 * | --- | --- |
 * | chapter with a title | the title |
 * | chapter, numbered | `Chapter 12` |
 * | chapter, unnumbered, volume known | `Volume 3` |
 * | chapter, unnumbered, no volume | `Single Volume` |
 * | volume, numbered | volume name, else `Volume 3` |
 * | volume, loose leaf | `Loose Leaf Chapters` |
 * | volume, specials | `Specials` |
 */
object EntityTitles {

    @Composable
    @ReadOnlyComposable
    fun chapter(chapter: Chapter): String {
        if (chapter.title.isNotBlank()) return chapter.title
        chapter.displayNumber?.let { return stringResource(R.string.entity_chapter_num, it) }
        // No chapter number: fall back to the volume it lives in, if that is meaningful.
        val volumeTitle = chapter.volumeTitle
        if (!volumeTitle.isNullOrBlank() && !volumeTitle.startsWith("-")) return volumeTitle
        return stringResource(R.string.entity_single_volume)
    }

    @Composable
    @ReadOnlyComposable
    fun volume(volume: Volume): String = when {
        volume.isLooseLeaf -> stringResource(R.string.entity_loose_leaf)
        volume.isSpecials -> stringResource(R.string.entity_specials)
        volume.name.isNotBlank() && !volume.name.startsWith("-") -> volume.name
        else -> stringResource(R.string.entity_volume_num, formatNumber(volume.minNumber))
    }

    /** Compact "Ch. 12"-style label used inside the reader overlay. */
    @Composable
    @ReadOnlyComposable
    fun chapterCompact(chapter: Chapter): String = when {
        chapter.title.isNotBlank() -> chapter.title
        chapter.displayNumber != null -> stringResource(R.string.entity_chapter_num, chapter.displayNumber!!)
        else -> stringResource(R.string.entity_single_volume)
    }

    /** Compact label used inside the reader overlay, where only the session is available. */
    @Composable
    @ReadOnlyComposable
    fun chapterCompact(session: com.kativa.reader.domain.model.ReaderSession): String {
        val title = session.chapterTitle
        if (title.isNotBlank() && !title.startsWith("-")) return title

        val number = session.chapterNumber
        if (number.isNotBlank() && !number.startsWith("-")) {
            val parsed = number.toFloatOrNull()
            val rendered = if (parsed != null) formatNumber(parsed) else number
            return stringResource(R.string.entity_chapter_num, rendered)
        }

        return stringResource(R.string.entity_single_volume)
    }

    /** Formats a float number without a trailing `.0` (Kavita stores floats such as 12.5). */
    fun formatNumber(value: Float): String =
        if (value % 1f == 0f) value.toLong().toString() else value.toString()
}
