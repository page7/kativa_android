package com.kavita.reader.core.designsystem

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The app's one and only top bar (AGENTS.md §6: components must come from `core/designsystem`).
 *
 * Light theme paints the bar with the brand mint (`primaryContainer` = #4AC694) so the header is
 * the strongest brand surface on the screen; dark theme keeps the near-black surface, because a
 * mint band at the top of a dark reader app would glare and fight the artwork below it.
 *
 * Content colour is not the M3 default: white on mint is ~2.2:1 and fails WCAG AA, so on the
 * brand bar every title/icon is drawn with `onPrimaryContainer` (near-black ink, ~9:1). While the
 * bar is brand-filled it also publishes [LocalOnBrandSurface], letting components that would
 * otherwise use a mint accent (the privacy toggle, the active filter icon, the search rule) switch
 * to an ink-based palette instead of disappearing into the background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KavitaTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val dark = LocalDarkTheme.current
    val colors = kavitaTopAppBarColors(dark)

    CompositionLocalProvider(
        LocalContentColor provides colors.titleContentColor,
        LocalOnBrandSurface provides !dark,
    ) {
        TopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = colors,
        )
    }
}

/**
 * Two-state icon tint for actions *inside* the top bar (active vs idle).
 *
 * On the light theme's mint band the brand accent would be invisible and the idle grey only
 * reaches ~3:1, so both states are drawn in the bar's own ink: full strength when active,
 * dimmed to 72% when idle (~5:1 on mint, still clearly dimmer than the active state).
 * Everywhere else the usual primary/onSurfaceVariant pair applies.
 */
@Composable
fun kavitaTopBarIconTint(active: Boolean): Color {
    return if (LocalOnBrandSurface.current) {
        KavitaColors.OnBrand.copy(alpha = if (active) 1f else 0.72f)
    } else {
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun kavitaTopAppBarColors(dark: Boolean): TopAppBarColors {
    val scheme = MaterialTheme.colorScheme
    return if (dark) {
        // Dark theme is deliberately untouched: it already drew its content on the near-black
        // surface from Material's defaults, so passing nothing keeps it pixel-identical.
        TopAppBarDefaults.topAppBarColors()
    } else {
        TopAppBarDefaults.topAppBarColors(
            // The brand band must not change shade when content scrolls under it: the M3 default
            // scrolled colour is a tinted surface, which would make the mint flip to grey
            // mid-scroll.
            containerColor = scheme.primaryContainer,
            scrolledContainerColor = scheme.primaryContainer,
            titleContentColor = scheme.onPrimaryContainer,
            navigationIconContentColor = scheme.onPrimaryContainer,
            actionIconContentColor = scheme.onPrimaryContainer,
        )
    }
}
