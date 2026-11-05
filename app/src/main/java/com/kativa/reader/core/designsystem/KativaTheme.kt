package com.kativa.reader.core.designsystem

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

@Composable
fun KativaTheme(
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
            typography = KativaTypography,
            content = content,
        )
    }
}

private val OnBrand = Color(0xFF07130E)
private val White = Color(0xFFFFFFFF)

private val DarkColors = darkColorScheme(
    primary = KativaColors.BrandPrimary,
    onPrimary = OnBrand,
    primaryContainer = KativaColors.BrandPrimaryDeep,
    onPrimaryContainer = KativaColors.TextPrimaryDark,
    secondary = KativaColors.BrandPrimaryDim,
    onSecondary = OnBrand,
    background = KativaColors.Background,
    onBackground = KativaColors.TextPrimaryDark,
    surface = KativaColors.Surface,
    onSurface = KativaColors.TextPrimaryDark,
    surfaceVariant = KativaColors.SurfaceElevated,
    onSurfaceVariant = KativaColors.TextSecondaryDark,
    outline = KativaColors.Outline,
    outlineVariant = KativaColors.Outline,
    error = KativaColors.Danger,
    onError = White,
)

private val LightColors = lightColorScheme(
    primary = KativaColors.BrandPrimaryDeep,
    onPrimary = White,
    primaryContainer = KativaColors.BrandPrimary,
    onPrimaryContainer = OnBrand,
    secondary = KativaColors.BrandPrimaryDim,
    onSecondary = White,
    background = KativaColors.BackgroundLight,
    onBackground = KativaColors.TextPrimaryLight,
    surface = KativaColors.SurfaceLight,
    onSurface = KativaColors.TextPrimaryLight,
    surfaceVariant = Color(0xFFF1F3F6),
    onSurfaceVariant = KativaColors.TextSecondaryLight,
    outline = KativaColors.OutlineLight,
    outlineVariant = KativaColors.OutlineLight,
    error = KativaColors.Danger,
    onError = White,
)
