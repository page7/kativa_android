package com.kativa.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.kativa.reader.R
import com.kativa.reader.core.designsystem.KativaIcon
import com.kativa.reader.core.designsystem.KativaIcons
import com.kativa.reader.core.designsystem.KativaColors
import com.kativa.reader.core.designsystem.KativaPrimaryButton
import com.kativa.reader.core.designsystem.KativaSecondaryButton
import com.kativa.reader.ui.common.message

/**
 * P08 — manga reader.
 *
 * Layout: full-bleed pager + tap-to-toggle overlay. Deliberately no bottom navigation, no
 * download affordance, and the privacy toggle is absent: privacy mode only masks covers, never
 * the pages being read (AGENTS.md §5/§6).
 */
@Composable
fun MangaReaderScreen(
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Flush queued progress whenever the app comes back to the foreground.
    LifecycleStartEffect(Unit) {
        viewModel.onAppResumed()
        onStopOrDispose { viewModel.onAppPaused() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            state.loading -> ReaderLoading()

            state.blockReason == ReaderBlockReason.NoNetwork -> ReaderMessage(
                title = stringResource(R.string.reader_no_network),
                action = stringResource(R.string.common_retry) to viewModel::load,
                onBack = onBack,
            )

            state.blockReason == ReaderBlockReason.UnsupportedFormat -> ReaderMessage(
                title = stringResource(R.string.reader_unsupported_format),
                action = null,
                onBack = onBack,
            )

            state.error != null -> ReaderMessage(
                title = state.error!!.message(),
                action = stringResource(R.string.common_retry) to viewModel::load,
                onBack = onBack,
            )

            state.session != null -> {
                val session = state.session!!
                val pageUrls = state.pageUrls
                val pagerState = rememberPagerState(
                    initialPage = state.pageIndex,
                    pageCount = { session.totalPages },
                )

                // Keep the pager and the VM in sync in both directions.
                LaunchedEffect(pagerState.currentPage) {
                    viewModel.onPageChanged(pagerState.currentPage)
                }
                LaunchedEffect(state.pageIndex) {
                    if (pagerState.currentPage != state.pageIndex && !pagerState.isScrollInProgress) {
                        pagerState.scrollToPage(state.pageIndex)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val third = size.width / 3f
                                    when {
                                        offset.x < third -> {
                                            if (pagerState.currentPage > 0) {
                                                viewModel.goToPage(pagerState.currentPage - 1)
                                            }
                                        }

                                        offset.x > third * 2 -> {
                                            if (pagerState.currentPage < session.totalPages - 1) {
                                                viewModel.goToPage(pagerState.currentPage + 1)
                                            }
                                        }

                                        else -> viewModel.toggleControls()
                                    }
                                },
                                onDoubleTap = { viewModel.toggleBookmark() },
                            )
                        },
                ) { index ->
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(pageUrls.getOrNull(index))
                            .build(),
                        contentDescription = stringResource(R.string.reader_page, index + 1, session.totalPages),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (state.controlsVisible) {
                    ReaderControls(
                        state = state,
                        onBack = onBack,
                        onSeek = viewModel::goToPage,
                        onToggleBookmark = viewModel::toggleBookmark,
                        onPrevChapter = viewModel::requestPrevChapter,
                        onNextChapter = viewModel::requestNextChapter,
                    )
                }

                if (state.isLastPage && !state.controlsVisible) {
                    ChapterEndBar(
                        onNextChapter = { viewModel.requestNextChapter() },
                        onBack = onBack,
                    )
                }

                // Navigate once the VM resolved a target chapter id.
                LaunchedEffect(state.pendingChapterNavigation) {
                    state.pendingChapterNavigation?.let { target ->
                        viewModel.consumeNavigationTarget()
                        onOpenChapter(target)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = KativaColors.BrandPrimary)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.reader_preparing),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ReaderMessage(
    title: String,
    action: Pair<String, () -> Unit>?,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            if (action != null) {
                KativaPrimaryButton(text = action.first, onClick = action.second)
                Spacer(Modifier.height(10.dp))
            }
            KativaSecondaryButton(text = stringResource(R.string.common_back), onClick = onBack)
        }
    }
}

@Composable
private fun ReaderControls(
    state: ReaderUiState,
    onBack: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleBookmark: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
) {
    val session = state.session ?: return
    Box(Modifier.fillMaxSize()) {
        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    KativaIcon(icon = KativaIcons.Back, contentDescription = stringResource(R.string.common_back), tint =  Color.White,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = session.seriesName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = session.chapterTitle.ifBlank { "Ch. ${session.chapterNumber}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onToggleBookmark) {
                    KativaIcon(icon = if (state.pageIndex in state.bookmarkedPages) {
                            KativaIcons.BookmarkFilled
                        } else {
                            KativaIcons.Bookmark
                        }, contentDescription = stringResource(R.string.reader_bookmark),
                        tint = if (state.pageIndex in state.bookmarkedPages) {
                            KativaColors.BrandPrimary
                        } else {
                            Color.White
                        },
                    )
                }
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevChapter, enabled = true) {
                        Text("‹", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    }
                    Text(
                        text = stringResource(R.string.reader_page, state.displayPage, state.totalPages),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = onNextChapter) {
                        Text("›", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Slider(
                    value = state.pageIndex.toFloat(),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..((state.totalPages - 1).coerceAtLeast(1)).toFloat(),
                )
            }
        }
    }
}

@Composable
private fun ChapterEndBar(
    onNextChapter: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            color = Color.Black.copy(alpha = 0.8f),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.reader_chapter_finished),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                KativaPrimaryButton(
                    text = stringResource(R.string.reader_next_chapter),
                    onClick = onNextChapter,
                )
                IconButton(onClick = onBack) {
                    KativaIcon(icon = KativaIcons.Back, contentDescription = stringResource(R.string.common_back), tint =  Color.White,
                    )
                }
            }
        }
    }
}
