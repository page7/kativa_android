package com.kavita.reader.domain.model

/**
 * Kavita's internal sentinel numbering. These are NOT real volume/chapter numbers — they encode
 * "no number was parsed" and "special" respectively, and must never be shown to the user.
 *
 * Source of truth: `Kavita.Models/Constants/ParserConstants.cs`
 * ```
 * DefaultChapter = "-100000"; LooseLeafVolume = "-100000";
 * DefaultChapterNumber = -100_000; SpecialVolumeNumber = 100_000;
 * ```
 * Changed in Kavita v0.8.0: loose leaf used to be 0, so that Volume 0 became representable.
 *
 * The reference web UI does the same thing in
 * `UI/Web/src/app/_services/entity-title.service.ts`; its backend even filters these chapters out
 * entirely (`SeriesService.ShouldIncludeChapter`).
 */
object KavitaNumbering {
    /** `-100000` — no chapter number could be parsed from the file. */
    const val UNNUMBERED = -100_000f

    /** `100000` — the synthetic volume that holds specials. */
    const val SPECIALS_VOLUME = 100_000f

    fun isUnnumbered(number: Float?): Boolean = number != null && number == UNNUMBERED

    fun isSpecialsVolume(number: Float?): Boolean = number != null && number == SPECIALS_VOLUME

    /** True for the synthetic volumes that must be surfaced separately from regular volumes. */
    fun isSyntheticVolume(number: Float?): Boolean = isUnnumbered(number) || isSpecialsVolume(number)

    /**
     * True when any of the given strings still contains a sentinel number.
     *
     * Needed because Kavita's server pre-formats `ChapterDto.Title` into strings such as
     * `"Chapter -100000"` / `"Volume 100000"`, so checking only the numeric fields is not enough.
     */
    fun containsSentinel(vararg values: String?): Boolean = values.any { value ->
        if (value.isNullOrBlank()) return@any false
        SENTINEL_TOKENS.any { token -> Regex("(?<![0-9])${Regex.escape(token)}(?![0-9])").containsMatchIn(value) }
    }

    private val SENTINEL_TOKENS = listOf("-100000", "100000")
}
