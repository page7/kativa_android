package com.kativa.reader.ui.reader

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.kativa.reader.R
import com.kativa.reader.core.designsystem.KativaColors
import com.kativa.reader.core.designsystem.KativaIcon
import com.kativa.reader.core.designsystem.KativaIcons
import com.kativa.reader.core.designsystem.KativaPrimaryButton
import com.kativa.reader.core.designsystem.KativaSecondaryButton
import com.kativa.reader.core.designsystem.KativaSlider
import com.kativa.reader.ui.common.message
import kotlin.math.roundToInt

/**
 * P08 —manga reader.
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

    // Hardware key events only reach the composition through the focused node, so the reader
    // claims focus while it is on screen (AGENTS.md §5: 音量键翻页（可关）).
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.loading) {
        if (!state.loading) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    // Flush queued progress whenever the app comes back to the foreground.
    LifecycleStartEffect(Unit) {
        viewModel.onAppResumed()
        onStopOrDispose { viewModel.onAppPaused() }
    }

    // AGENTS.md §5: 防息屏. Kept as a plain view flag rather than a window flag so it is cleared
    // automatically when the reader leaves composition.
    //
    // The reader is always dark, so the system icon appearance is forced to light while it is on
    // screen: `KativaTheme` otherwise hands dark icons to a light-themed app, which would be
    // invisible over the black scrim behind the status bar. The previous value is restored on
    // exit so every other screen keeps the theme's own choice.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightStatus = controller?.isAppearanceLightStatusBars
        val previousLightNav = controller?.isAppearanceLightNavigationBars
        controller?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        onDispose {
            view.keepScreenOn = false
            if (controller != null) {
                previousLightStatus?.let { controller.isAppearanceLightStatusBars = it }
                previousLightNav?.let { controller.isAppearanceLightNavigationBars = it }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .then(
                if (state.settings.volumeKeyPaging) {
                    Modifier.onPreviewKeyEvent { event ->
                        viewModel.handleVolumeKey(event.nativeKeyEvent)
                    }
                } else {
                    Modifier
                },
            ),
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
                val settings = state.settings

                // Webtoon is a completely different composition (continuous scroll), not a pager
                // variant, so it branches here rather than inside the pager (AGENTS.md §5).
                //
                // Both branches must fall through to the shared overlay/controls/settings below.
                // An earlier version wrote `return@Box` for the Webtoon case, which skipped the
                // `ReaderSettingsSheet` composed further down in this same Box lambda: switching to
                // scroll mode then made the sheet unreachable and trapped the user in it
                // (ReaderChrome.showsSettingsSheet / ReaderModeSettingsTest).
                if (ReaderChrome.usesContinuousScroll(settings.readerMode)) {
                    WebtoonReader(
                        pageUrls = pageUrls,
                        totalPages = session.totalPages,
                        startPage = ReaderChrome.webtoonStartIndex(
                            pageIndex = state.pageIndex,
                            totalPages = session.totalPages,
                        ),
                        onPageVisible = viewModel::onPageChanged,
                        onTap = viewModel::toggleControls,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    PagedReader(
                        state = state,
                        pageUrls = pageUrls,
                        context = context,
                        onGoToPage = viewModel::goToPage,
                        onToggleControls = viewModel::toggleControls,
                        // Double-tap is the second bookmark affordance and must obey the same
                        // role check as the toolbar icon (docs/FEATURES.md F-17).
                        onToggleBookmark = {
                            if (state.canBookmark) viewModel.toggleBookmark()
                        },
                    )
                }

                ReaderOverlay(state)

                if (state.controlsVisible) {
                    ReaderControls(
                        state = state,
                        onBack = onBack,
                        onSeek = viewModel::goToPage,
                        onToggleBookmark = viewModel::toggleBookmark,
                        onPrevChapter = viewModel::requestPrevChapter,
                        onNextChapter = viewModel::requestNextChapter,
                        onOpenSettings = viewModel::openSettings,
                    )
                }

                // "Chapter finished" is a pager concept: in continuous scroll the last page is
                // simply the bottom of the list, and a floating bar there would cover content.
                if (state.isLastPage && !state.controlsVisible &&
                    !ReaderChrome.usesContinuousScroll(settings.readerMode)
                ) {
                    ChapterEndBar(
                        onNextChapter = { viewModel.requestNextChapter() },
                        onBack = onBack,
                    )
                }

                ReaderNavigationEffect(state, viewModel, onOpenChapter)
            }
        }

        if (ReaderChrome.showsSettingsSheet(state.settingsSheetVisible, state.settings.readerMode)) {
            ReaderSettingsSheet(
                settings = state.settings,
                onDismiss = viewModel::closeSettings,
                onReadingDirection = viewModel::setReadingDirection,
                onReaderMode = viewModel::setReaderMode,
                onPageLayout = viewModel::setPageLayout,
                onScaling = viewModel::setScaling,
                onCoverOffset = viewModel::setCoverOffset,
                onBrightness = viewModel::setBrightness,
                onShowPageNumber = viewModel::setShowPageNumber,
                onVolumeKeyPaging = viewModel::setVolumeKeyPaging,
            )
        }
    }
}

@Composable
private fun ReaderNavigationEffect(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    onOpenChapter: (Int) -> Unit,
) {
    LaunchedEffect(state.pendingChapterNavigation) {
        state.pendingChapterNavigation?.let { target ->
            viewModel.consumeNavigationTarget()
            onOpenChapter(target)
        }
    }
}

/** Luminance scrim + optional faint page marker, drawn below the controls and above the pages. */
@Composable
private fun ReaderOverlay(state: ReaderUiState) {
    val dim = ReaderDim.alphaFor(state.settings.brightness)
    if (dim <= 0f && !state.settings.showPageNumber) return

    Box(Modifier.fillMaxSize()) {
        if (dim > 0f) {
            // Drawn above the pages, below the controls. `background` does not consume pointer
            // input, so tap zones and paging gestures keep working through the scrim.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim)),
            )
        }
        if (state.settings.showPageNumber) {
            Text(
                text = "${state.displayPage}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.35f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // Keep the marker out of the system navigation bar strip.
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.End,
                        ),
                    )
                    .padding(end = 12.dp, bottom = 12.dp),
            )
        }
    }
}

/** Paged (single/double page) reading: horizontal or vertical pager plus tap zones. */
@Composable
private fun PagedReader(
    state: ReaderUiState,
    pageUrls: List<String>,
    context: android.content.Context,
    onGoToPage: (Int) -> Unit,
    onToggleControls: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    val settings = state.settings
    val pagerState = rememberPagerState(
        initialPage = state.currentSlot,
        pageCount = { state.slotCount },
    )

    // Keep the pager and the VM in sync in both directions.
    LaunchedEffect(pagerState.currentPage) {
        val slotPages = state.slotPages(pagerState.currentPage)
        // Persist the leading page of a double spread; that is what the user sees first.
        slotPages.firstOrNull()?.let { page -> onGoToPage(page - 1) }
    }
    LaunchedEffect(state.currentSlot) {
        if (pagerState.currentPage != state.currentSlot && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(state.currentSlot)
        }
    }

    val goNext = {
        if (pagerState.currentPage < state.slotCount - 1) {
            onGoToPage(state.slotPages(pagerState.currentPage + 1).firstOrNull()?.minus(1) ?: 0)
        }
    }
    val goPrev = {
        if (pagerState.currentPage > 0) {
            onGoToPage(state.slotPages(pagerState.currentPage - 1).firstOrNull()?.minus(1) ?: 0)
        }
    }

    // Taps on the chrome bars never reach this node: the bars own a hit-test node of their own
    // (see `readerChromeTouchGuard`), which is what stops a seek drag on the progress slider from
    // being arbitrated into a page swipe.
    val rtl = settings.readingDirectionApplies && settings.isRightToLeft
    val pagerModifier = Modifier
        .fillMaxSize()
        .pointerInput(settings.readerMode, settings.layoutMode, settings.readingDirection) {
            detectTapGestures(
                onTap = { offset ->
                    val third = size.width / 3f
                    // Tap zones follow the reading direction: in RTL the left third advances,
                    // mirroring how manga is read. Vertical paging has no horizontal direction, so
                    // it keeps LTR tap zones (`rtl` is false there).
                    when {
                        offset.x < third -> if (rtl) goNext() else goPrev()
                        offset.x > third * 2 -> if (rtl) goPrev() else goNext()
                        else -> onToggleControls()
                    }
                },
                onDoubleTap = { onToggleBookmark() },
            )
        }

    if (settings.isVerticalPaging) {
        VerticalPager(
            state = pagerState,
            modifier = pagerModifier,
            // Vertical reading always advances downwards; RTL would be meaningless here.
            reverseLayout = false,
        ) { index ->
            PagerSlotContent(
                state = state,
                slotIndex = index,
                pageUrls = pageUrls,
                context = context,
            )
        }
    } else {
        HorizontalPager(
            state = pagerState,
            modifier = pagerModifier,
            reverseLayout = rtl,
        ) { index ->
            PagerSlotContent(
                state = state,
                slotIndex = index,
                pageUrls = pageUrls,
                context = context,
            )
        }
    }
}

/** One pager slot: a single page, or a facing pair in double-page mode. */
@Composable
private fun PagerSlotContent(
    state: ReaderUiState,
    slotIndex: Int,
    pageUrls: List<String>,
    context: android.content.Context,
) {
    val pages = state.slotPages(slotIndex)
    val total = state.totalPages
    // Honours the sheet's "image scaling" choice; previously every page was hard-coded to Fit and
    // the control did nothing (AGENTS.md §6 dead-control rule).
    val scale = ReaderScaling.contentScale(state.settings.scalingOption)

    if (pages.size <= 1) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(pageUrls.getOrNull((pages.firstOrNull() ?: 1) - 1)).build(),
            contentDescription = stringResource(
                R.string.reader_page,
                pages.firstOrNull() ?: 1,
                total,
            ),
            contentScale = scale,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    Row(Modifier.fillMaxSize()) {
        // RTL puts the later page on the left, which is how right-to-left spreads read. Slots in
        // double-page mode only exist for horizontal paging, so `isRightToLeft` is meaningful here.
        val ordered = if (state.settings.isRightToLeft) pages.reversed() else pages
        ordered.forEach { page ->
            AsyncImage(
                model = ImageRequest.Builder(context).data(pageUrls.getOrNull(page - 1)).build(),
                contentDescription = stringResource(R.string.reader_page, page, total),
                contentScale = scale,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
    }
}

/** Continuous vertical scroll used by Webtoon mode. */
@Composable
private fun WebtoonReader(
    pageUrls: List<String>,
    totalPages: Int,
    /** 0-based page to start at, i.e. the reading position carried over when switching modes. */
    startPage: Int,
    onPageVisible: (Int) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Seeded from the current reading position. Without this the list always started at page 1
    // and immediately wrote that position back, so switching into Webtoon silently rewound the
    // chapter — and the user's saved progress with it.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage.coerceAtLeast(0))

    // Report the first mostly-visible page so progress matches what the user is looking at.
    val visiblePage by remember {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo
            info.firstOrNull { item ->
                item.offset + item.size / 2 >= 0
            }?.index?.plus(1) ?: (startPage + 1).coerceAtLeast(1)
        }
    }
    LaunchedEffect(visiblePage) { onPageVisible(visiblePage - 1) }

    LazyColumn(
        state = listState,
        modifier = modifier.pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
    ) {
        items(totalPages) { index ->
            AsyncImage(
                model = ImageRequest.Builder(context).data(pageUrls.getOrNull(index)).build(),
                contentDescription = stringResource(R.string.reader_page, index + 1, totalPages),
                // Webtoon strips are width-bound by definition, so this stays FillWidth regardless
                // of `scalingOption`; that is why the sheet hides the scaling control in this mode
                // rather than offering a choice the renderer would ignore.
                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
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
    onOpenSettings: () -> Unit,
) {
    val session = state.session ?: return
    Box(Modifier.fillMaxSize()) {
        TopChrome {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Content clears the status bar (and a notch/cutout in landscape), while the
                    // scrim above it keeps painting behind the system bar strip.
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
                        text = com.kativa.reader.ui.common.EntityTitles.chapterCompact(session),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.canBookmark) {
                    IconButton(onClick = onToggleBookmark) {
                        KativaIcon(
                            icon = if (state.pageIndex in state.bookmarkedPages) {
                                KativaIcons.BookmarkFilled
                            } else {
                                KativaIcons.Bookmark
                            },
                            contentDescription = stringResource(R.string.reader_bookmark),
                            tint = if (state.pageIndex in state.bookmarkedPages) {
                                KativaColors.BrandPrimary
                            } else {
                                Color.White
                            },
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    KativaIcon(
                        icon = KativaIcons.SettingsReader,
                        contentDescription = stringResource(R.string.reader_settings),
                        tint = Color.White,
                    )
                }
            }
        }

        BottomChrome {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Content clears the navigation bar (and the gesture handle), while the scrim
                    // keeps painting behind it.
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                        ),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevChapter, enabled = true) {
                        KativaIcon(
                            icon = KativaIcons.PrevChapter,
                            contentDescription = stringResource(R.string.reader_prev_chapter),
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = stringResource(R.string.reader_page, state.displayPage, state.totalPages),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = onNextChapter) {
                        KativaIcon(
                            icon = KativaIcons.NextChapter,
                            contentDescription = stringResource(R.string.reader_next_chapter),
                            tint = Color.White,
                        )
                    }
                }
                var dragValue by remember { mutableStateOf<Float?>(null) }

                KativaSlider(
                    value = dragValue ?: state.pageIndex.toFloat(),
                    // While dragging only the local state moves: driving the pager from every
                    // frame made the pages jitter and restarted the 800ms progress debounce
                    // continuously. The jump happens once, on release.
                    onValueChange = { dragValue = it },
                    onValueChangeFinished = {
                        dragValue?.let { onSeek(it.roundToInt()) }
                        dragValue = null
                    },
                    valueRange = 0f..((state.totalPages - 1).coerceAtLeast(0)).toFloat(),
                    activeColor = Color.White,
                    trackColor = Color.White.copy(alpha = 0.22f),
                    valueLabel = { value ->
                        stringResource(
                            R.string.reader_page,
                            value.roundToInt() + 1,
                            state.totalPages,
                        )
                    },
                )
            }
        }
    }
}

/**
 * Top reader chrome: a translucent scrim that reaches the very top of the screen (drawing behind
 * the status bar) while its *content* is inset below the status bar, plus a short fade so the
 * boundary with the artwork is not a hard cut.
 *
 * Only the solid part is opaque to touch: the gradient tail stays transparent so a tap or swipe
 * that lands there still reaches the pager.
 */
@Composable
private fun BoxScope.TopChrome(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                // Touches on the bar must not fall through to the pager underneath: this plain
                // container has no gesture node of its own, so without the guard a press on the
                // bar's padding hit-tested straight through and became a page swipe.
                .readerChromeTouchGuard()
                .background(KativaColors.ReaderScrim),
        ) {
            content()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(ChromeFadeHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(KativaColors.ReaderScrim, Color.Transparent),
                    ),
                ),
        )
    }
}

/** Bottom reader chrome: fade into the bar, then a scrim behind the navigation bar too. */
@Composable
private fun BoxScope.BottomChrome(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ChromeFadeHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, KativaColors.ReaderScrim),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .readerChromeTouchGuard()
                .background(KativaColors.ReaderScrim),
        ) {
            content()
        }
    }
}

/**
 * Makes a chrome bar a hit-test target without intercepting anything.
 *
 * Compose hit-tests overlapping siblings from the top down and stops at the first sibling that
 * owns a pointer-input node. The bars were previously plain layout containers, so a touch on the
 * bar's padding or in the gap between the slider's thumb and its 48dp strip found no node there
 * and fell straight through to the pager below — which read the slide as a page swipe.
 *
 * The fix is an inert gesture node: it is never consumed, so the back/bookmark/settings buttons
 * and the progress slider keep their own handling (consuming here would make `clickable` cancel
 * the tap on the Final pass), while the pager underneath stops receiving the touch at all.
 */
private fun Modifier.readerChromeTouchGuard(): Modifier = pointerInput(Unit) {
    awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
}

/** Height of the gradient tail that softens the chrome/page boundary. */
private val ChromeFadeHeight = 20.dp

@Composable
private fun ChapterEndBar(
    onNextChapter: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            color = Color.Black.copy(alpha = 0.8f),
            modifier = Modifier
                .fillMaxWidth()
                // Clear the navigation bar *and* the bottom chrome it sits under.
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(16.dp),
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
