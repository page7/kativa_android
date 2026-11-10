package com.kavita.reader.core.designsystem

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.kavita.reader.R

/**
 * The only pull-to-refresh surface screens should use.
 *
 * Wrapping Material's `PullToRefreshBox` here (rather than at each call site) keeps two rules in
 * one place:
 *  - the spinner uses the brand mint over the surface colour, matching `KavitaSlider` /
 *    `KavitaProgressBar` — M3's default indicator would drop in an off-palette container;
 *  - TalkBack gets an explicit "refresh" action, because a pull gesture is unreachable for a
 *    screen-reader user (AGENTS.md §6: every interaction needs an accessible equivalent).
 *
 * The content must be scrollable (`LazyColumn`, `LazyVerticalGrid`, …) for the gesture to fire;
 * use [RefreshablePlaceholder] for static states such as empty or error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KavitaPullToRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val refreshLabel = stringResource(R.string.common_refresh)
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(label = refreshLabel) {
                    onRefresh()
                    true
                },
            )
        },
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surface,
                color = KavitaColors.BrandPrimary,
            )
        },
        content = content,
    )
}

/**
 * Makes a non-scrolling state (empty, error) pullable.
 *
 * A bare `Box` gives the nested-scroll machinery nothing to drag, so "pull down to retry" silently
 * did nothing on exactly the screens where the user most wants it. The content is given a minimum
 * height of one viewport and made scrollable, which enables the gesture without introducing a
 * visible scrollbar or moving the content off centre.
 */
@Composable
fun RefreshablePlaceholder(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

/**
 * Short, non-blocking confirmation.
 *
 * A Toast rather than a Snackbar: a confirmation that needs no action must not occupy layout,
 * cover content, or wait to be dismissed. Snackbars stay reserved for failures the user may need
 * to act on (see the series-detail error host).
 *
 * The application context is captured once, so holding the lambda past a configuration change
 * cannot leak the Activity.
 */
@Composable
fun rememberToast(): (String) -> Unit {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        { message: String -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}
