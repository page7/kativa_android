package com.kativa.reader.ui.library

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kativa.reader.core.designsystem.EmptyState
import com.kativa.reader.core.designsystem.KativaIcon
import com.kativa.reader.core.designsystem.KativaIcons
import com.kativa.reader.core.designsystem.KativaTopAppBar
import com.kativa.reader.core.designsystem.SeriesPosterCard
import com.kativa.reader.core.designsystem.kativaTopBarIconTint
import com.kativa.reader.ui.common.PrivacyToggle

/** P05 锟?series grid for one library, with sort and infinite scroll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryGridScreen(
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    viewModel: LibraryGridViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    var filterOpen by remember { mutableStateOf(false) }

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
            KativaTopAppBar(
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
                    IconButton(onClick = { filterOpen = true }) {
                        KativaIcon(
                            icon = KativaIcons.Sort,
                            contentDescription = stringResource(R.string.filter_title),
                            tint = kativaTopBarIconTint(state.hasActiveFilter),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null && state.items.isEmpty() ->
                    com.kativa.reader.ui.common.KativaErrorState(
                        error = state.error!!,
                        onRetry = { viewModel.load(reset = true) },
                    )

                state.isEmptyDueToFilter -> EmptyState(
                    title = stringResource(R.string.filter_no_match),
                    body = stringResource(R.string.filter_no_match_body),
                    actionLabel = stringResource(R.string.filter_clear),
                    onAction = viewModel::clearFilter,
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
                    // Active filter chips, so the user can see (and undo) what is applied.
                    if (!state.filter.isEmpty) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ActiveFilterChips(
                                filter = state.filter,
                                onClearName = { viewModel.applyFilter(state.filter.withNameQuery("")) },
                                onClearWantToRead = { viewModel.applyFilter(state.filter.withWantToRead(null)) },
                                onClear = viewModel::clearFilter,
                            )
                        }
                    }

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

    if (filterOpen) {
        com.kativa.reader.ui.common.FilterSheet(
            initial = state.filter,
            onDismiss = { filterOpen = false },
            onApply = { filter ->
                filterOpen = false
                viewModel.applyFilter(filter)
            },
        )
    }
}

/**
 * Chips for the conditions currently applied.
 *
 * Only two fields can be active (name query, want-to-read), so each chip maps to one field
 * rather than to a raw statement index: tapping it drops exactly that condition.
 */
@Composable
private fun ActiveFilterChips(
    filter: com.kativa.reader.domain.model.SeriesFilter,
    onClearName: () -> Unit,
    onClearWantToRead: () -> Unit,
    onClear: () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        filter.nameQuery.takeIf { it.isNotEmpty() }?.let { query ->
            item(key = "name") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onClearName,
                ) {
                    Text(
                        text = stringResource(R.string.filter_chip_name, query) + "  " + CloseMark,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        maxLines = 1,
                    )
                }
            }
        }
        if (filter.wantToRead == true) {
            item(key = "want") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onClearWantToRead,
                ) {
                    Text(
                        text = stringResource(R.string.filter_want_to_read_only) + "  " + CloseMark,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        maxLines = 1,
                    )
                }
            }
        }
        item(key = "clear") {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.filter_clear), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Multiplied sign, used as the "remove" affordance on a chip (avoids a new asset). */
private const val CloseMark = "\u00D7"
