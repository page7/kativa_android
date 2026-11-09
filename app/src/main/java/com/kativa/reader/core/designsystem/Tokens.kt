package com.kativa.reader.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Brand palette. See AGENTS.md §6.
 *
 * Light theme uses [BrandPrimary]; dark theme uses [BrandPrimaryDeep] as the container colour
 * and keeps [BrandPrimary] for accents, so the mint stays recognisable on dark backgrounds.
 */
object KativaColors {
    val BrandPrimary = Color(0xFF4AC694)
    val BrandPrimaryDeep = Color(0xFF2D7A56)
    val BrandPrimaryDim = Color(0xFF3AA87B)

    /**
     * Content colour that sits *on* [BrandPrimary]: near-black with a green cast.
     *
     * White on the mint fails WCAG AA (~2.2:1); this ink reaches ~9:1, which is why every
     * brand-coloured surface (top bar, primary buttons, selected segments) paints its text and
     * icons with it instead of `onPrimary`/`White`.
     */
    val OnBrand = Color(0xFF07130E)

    val Background = Color(0xFF0B0B0F)
    val Surface = Color(0xFF15151C)
    val SurfaceElevated = Color(0xFF1D1D26)
    val Outline = Color(0xFF2C2C38)

    val BackgroundLight = Color(0xFFFAFAFB)
    val SurfaceLight = Color(0xFFFFFFFF)
    val OutlineLight = Color(0xFFE2E4E9)

    val Warning = Color(0xFFF59E0B)
    val Danger = Color(0xFFE5484D)
    val Success = Color(0xFF4AC694)

    /**
     * Backdrop behind every cover when privacy mode is on.
     *
     * Dark theme keeps the deep grey (#3A3A3A): a light tile would glare at night, which defeats
     * the point of masking the artwork. The light theme needs its own value — #3A3A3A read as a
     * black hole among white cards — so it uses a light grey (#E9EBEF) one step below the surface,
     * which still reads as "deliberately blank" rather than "failed to load".
     * Read through [privacyFill] so the right one follows the active theme.
     */
    val PrivacyFill = Color(0xFF3A3A3A)
    val PrivacyFillLight = Color(0xFFE9EBEF)

    /**
     * Reader chrome scrim (AGENTS.md §5/§6).
     *
     * The reader is edge-to-edge, so the top/bottom bars sit on top of the artwork *and* the
     * system status/navigation bar strips. A translucent black scrim (75%, close to the 72% the
     * bars used before they were extended over the system bars) keeps the page faintly visible
     * while still giving the system icons and the bar content enough contrast.
     */
    val ReaderScrim = Color(0xC0000000)

    /**
     * Folder tile background: a translucent grey veil rather than a solid fill.
     *
     * Dark theme needs a *white* veil to lift the tile off the near-black background; light theme
     * needs a *black* one. Both stay genuinely translucent, so the page shows through.
     */
    val FolderSurfaceDark = Color(0x1FFFFFFF)
    val FolderSurfaceLight = Color(0x14000000)

    /**
     * Bottom gradient behind poster titles (SeriesPosterCard), which every list uses.
     *
     * Dark theme darkens the bottom of the cover so white ink stays readable. Light theme must
     * do the *inverse*: the black veil read as a grey smudge across every card in light mode, so
     * it fades to white instead and the title flips to black. Read through the theme split in
     * [SeriesPosterCard]; both values are ≥ 72% opaque so the artwork under the title never
     * competes with the text.
     */
    val PosterScrimDark = Color(0xB8000000)
    val PosterScrimLight = Color(0xE6FFFFFF)

    val TextPrimaryDark = Color(0xFFF2F3F5)
    val TextSecondaryDark = Color(0xFF9AA0AA)
    val TextPrimaryLight = Color(0xFF15161A)
    val TextSecondaryLight = Color(0xFF5B6170)
}

object KativaDimens {
    val Grid = 4
    val ScreenPadding = 16
    val CardRadius = 12
    val PosterRadius = 12
    val PosterAspect = 2f / 3f
    val TouchTarget = 48
    val IconSize = 24
    val SectionSpacing = 24
}

val KativaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)
