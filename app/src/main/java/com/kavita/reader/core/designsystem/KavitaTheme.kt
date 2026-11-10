package com.kavita.reader.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** User-selected theme mode (persisted). */
enum class ThemeMode { System, Dark, Light }

/**
 * Privacy mode state exposed to the whole tree.
 *
 * Cover composables must check this *before* enqueueing an image request:
 * privacy mode must not merely mask pixels after download. See AGENTS.md 鎼?.
 */
val LocalPrivacyMode = compositionLocalOf { false }

/** True while a screen wants the top-bar privacy toggle visible. */
val LocalDarkTheme = staticCompositionLocalOf { true }

/**
 * True when the composable sits on a brand-coloured (mint) surface, as the light-theme top bar
 * does. Accent colours that normally use [KavitaColors.BrandPrimary] must fall back to an
 * ink-based tone there — mint on mint is invisible. See [KavitaTopAppBar].
 */
val LocalOnBrandSurface = staticCompositionLocalOf { false }

@Composable
fun KavitaTheme(
    themeMode: ThemeMode = ThemeMode.System,
    privacyMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }

    val colorScheme = if (dark) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalPrivacyMode provides privacyMode,
        LocalDarkTheme provides dark,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KavitaTypography,
            content = content,
        )
    }
}

private val OnBrand = KavitaColors.OnBrand
private val White = Color(0xFFFFFFFF)

private val DarkColors = darkColorScheme(
    primary = KavitaColors.BrandPrimary,
    onPrimary = OnBrand,
    primaryContainer = KavitaColors.BrandPrimaryDeep,
    onPrimaryContainer = KavitaColors.TextPrimaryDark,
    secondary = KavitaColors.BrandPrimaryDim,
    onSecondary = OnBrand,
    background = KavitaColors.Background,
    onBackground = KavitaColors.TextPrimaryDark,
    surface = KavitaColors.Surface,
    onSurface = KavitaColors.TextPrimaryDark,
    surfaceVariant = KavitaColors.SurfaceElevated,
    onSurfaceVariant = KavitaColors.TextSecondaryDark,
    outline = KavitaColors.Outline,
    outlineVariant = KavitaColors.Outline,
    secondaryContainer = KavitaColors.BrandPrimaryDeep,
    onSecondaryContainer = KavitaColors.TextPrimaryDark,
    inverseSurface = KavitaColors.TextPrimaryDark,
    inverseOnSurface = KavitaColors.Background,
    surfaceContainer = KavitaColors.Surface,
    surfaceContainerHigh = KavitaColors.SurfaceElevated,
    surfaceContainerHighest = KavitaColors.SurfaceElevated,
    error = KavitaColors.Danger,
    onError = White,
)

private val LightColors = lightColorScheme(
    primary = KavitaColors.BrandPrimaryDeep,
    onPrimary = White,
    primaryContainer = KavitaColors.BrandPrimary,
    onPrimaryContainer = OnBrand,
    secondary = KavitaColors.BrandPrimaryDim,
    onSecondary = White,
    background = KavitaColors.BackgroundLight,
    onBackground = KavitaColors.TextPrimaryLight,
    surface = KavitaColors.SurfaceLight,
    onSurface = KavitaColors.TextPrimaryLight,
    surfaceVariant = Color(0xFFF1F3F6),
    onSurfaceVariant = KavitaColors.TextSecondaryLight,
    outline = KavitaColors.OutlineLight,
    outlineVariant = KavitaColors.OutlineLight,
    secondaryContainer = Color(0xFFDDF3E8),
    onSecondaryContainer = KavitaColors.TextPrimaryLight,
    inverseSurface = KavitaColors.TextPrimaryLight,
    inverseOnSurface = Color.White,
    surfaceContainer = Color(0xFFF1F3F6),
    surfaceContainerHigh = Color(0xFFEBEEF2),
    surfaceContainerHighest = Color(0xFFE4E8ED),
    error = KavitaColors.Danger,
    onError = White,
)
