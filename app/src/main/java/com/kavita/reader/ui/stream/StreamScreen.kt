package com.kavita.reader.ui.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavita.reader.R
import com.kavita.reader.core.designsystem.EmptyState
import com.kavita.reader.core.designsystem.KavitaIcon
import com.kavita.reader.core.designsystem.KavitaIcons
import com.kavita.reader.core.designsystem.KavitaTopAppBar
import com.kavita.reader.core.designsystem.SeriesPosterCard
import com.kavita.reader.ui.common.PrivacyToggle

/**
 * "View all" target for a home stream: the same cards, paged, with no horizontal cap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    viewModel: StreamViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 8
        }
    }
    LaunchedEffect(shouldLoadMore, state.endReached, state.loadingMore) {
        if (shouldLoadMore && !state.endReached) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            KavitaTopAppBar(
                title = { Text(stringResource(viewModel.kind.titleRes)) },
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null && state.items.isEmpty() ->
                    com.kavita.reader.ui.common.KavitaErrorState(
                        error = state.error!!,
                        onRetry = { viewModel.load(reset = true) },
                    )

                state.isEmpty -> EmptyState(
                    title = stringResource(R.string.stream_empty_title),
                    body = stringResource(R.string.stream_empty_body),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = { viewModel.load(reset = true) },
                    illustration = R.drawable.empty_shelf,
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    state = gridState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.entries, key = { it.key }) { entry ->
                        SeriesPosterCard(
                            title = entry.item.series.name,
                            coverUrl = entry.item.coverUrl,
                            subtitle = entry.item.series.libraryName.takeIf { it.isNotBlank() },
                            progress = entry.item.series.progress.takeIf { it > 0f },
                            onClick = { onOpenSeries(entry.item.series.id) },
                        )
                    }

                    if (state.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}
