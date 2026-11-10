package com.kavita.reader.ui.bookmarks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavita.reader.R
import com.kavita.reader.core.designsystem.EmptyState
import com.kavita.reader.core.designsystem.KavitaIcon
import com.kavita.reader.core.designsystem.KavitaIcons
import com.kavita.reader.core.designsystem.KavitaPullToRefreshBox
import com.kavita.reader.core.designsystem.KavitaTopAppBar
import com.kavita.reader.core.designsystem.RefreshablePlaceholder
import com.kavita.reader.core.designsystem.SeriesPosterCard
import com.kavita.reader.ui.common.PrivacyToggle

/**
 * P14 — bookmarks reached from "Me".
 *
 * Thumbnails are `GET Image/bookmark` page captures, so they obey privacy mode exactly like
 * covers (`CoverImage` + `PrivacyGuardInterceptor`): masked, never fetched (docs/FEATURES.md F-17).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenBookmark: (chapterId: Int, pageIndex: Int) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Refresh on resume: bookmarks are edited inside the reader, so returning here must not show
    // a stale list (a bookmark removed in the reader used to stay on screen).
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { /* nothing to release */ }
    }

    Scaffold(
        topBar = {
            KavitaTopAppBar(
                title = { Text(stringResource(R.string.bookmarks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        KavitaIcon(
                            icon = KavitaIcons.Back,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    PrivacyToggle(
                        enabled = state.privacyMode,
                        onToggle = viewModel::setPrivacyMode,
                    )
                },
            )
        },
    ) { padding ->
        KavitaPullToRefreshBox(
            refreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null -> RefreshablePlaceholder {
                    com.kavita.reader.ui.common.KavitaErrorState(
                        error = state.error!!,
                        onRetry = viewModel::load,
                    )
                }

                state.isEmpty -> RefreshablePlaceholder {
                    EmptyState(
                        title = stringResource(R.string.bookmarks_empty_title),
                        body = stringResource(R.string.bookmarks_empty_body),
                        illustration = R.drawable.empty_shelf,
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.items, key = { it.bookmark.id }) { item ->
                        val bookmark = item.bookmark
                        val pageLabel = stringResource(R.string.bookmark_page_number, bookmark.displayPage)
                        val title = bookmark.seriesName.ifBlank { pageLabel }
                        SeriesPosterCard(
                            // `POST Reader/all-bookmarks` embeds the series; fall back to the page
                            // number so the card is never blank.
                            title = title,
                            coverUrl = item.thumbnailUrl,
                            // Chapter title is optional (blank for some formats); never repeat a
                            // subtitle that is identical to the title.
                            subtitle = bookmark.chapterTitle?.let { chapter ->
                                stringResource(R.string.bookmark_chapter_page, chapter, bookmark.displayPage)
                            } ?: pageLabel.takeIf { it != title },
                            // The bookmark list has no per-series progress to show.
                            progress = null,
                            onClick = { onOpenBookmark(bookmark.chapterId, bookmark.pageIndex) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
