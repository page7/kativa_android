package com.kativa.reader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kativa.reader.R
import com.kativa.reader.core.designsystem.KativaIcon
import com.kativa.reader.core.designsystem.KativaIcons
import com.kativa.reader.core.designsystem.EmptyState
import com.kativa.reader.core.designsystem.ErrorState
import com.kativa.reader.core.designsystem.SeriesPosterCard
import com.kativa.reader.ui.common.PrivacyToggle
import com.kativa.reader.ui.common.message

/** P05 — series grid for one library, with sort and infinite scroll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryGridScreen(
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    viewModel: LibraryGridViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    var sortOpen by remember { mutableStateOf(false) }

    // Prefetch when the user is ~4 rows from the end.
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 8
        }
    }
    LaunchedEffect(shouldLoadMore, state.endReached, state.loadingMore) {
        if (shouldLoadMore && !state.endReached) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_library)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        KativaIcon(icon = KativaIcons.Back, contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    PrivacyToggle(
                        enabled = state.privacyMode,
                        onToggle = viewModel::setPrivacyMode,
                    )
                    Box {
                        IconButton(onClick = { sortOpen = true }) {
                            KativaIcon(icon = KativaIcons.Sort, contentDescription = stringResource(R.string.common_more))
                        }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            SortOption(stringResource(R.string.series_volumes), 1, true) {
                                sortOpen = false
                                viewModel.setSort(1, true)
                            }
                            SortOption(stringResource(R.string.home_stream_recently_added), 2, false) {
                                sortOpen = false
                                viewModel.setSort(2, false)
                            }
                            SortOption(stringResource(R.string.home_stream_recently_updated), 3, false) {
                                sortOpen = false
                                viewModel.setSort(3, false)
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null && state.items.isEmpty() -> ErrorState(
                    message = state.error!!.message(),
                    retryLabel = stringResource(R.string.common_retry),
                    onRetry = { viewModel.load(reset = true) },
                )

                state.isEmpty -> EmptyState(
                    title = stringResource(R.string.library_detail_empty),
                    body = stringResource(R.string.home_empty_body),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = { viewModel.load(reset = true) },
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    state = gridState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.items, key = { it.series.id }) { item ->
                        SeriesPosterCard(
                            title = item.series.name,
                            coverUrl = item.coverUrl,
                            subtitle = item.series.libraryName.takeIf { it.isNotBlank() },
                            progress = item.series.progress.takeIf { it > 0f },
                            onClick = { onOpenSeries(item.series.id) },
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

@Composable
private fun SortOption(
    label: String,
    field: Int,
    ascending: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick)
}
