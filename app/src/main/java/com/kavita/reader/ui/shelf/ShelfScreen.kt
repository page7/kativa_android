package com.kavita.reader.ui.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavita.reader.R
import com.kavita.reader.core.designsystem.CoverImage
import com.kavita.reader.core.designsystem.EmptyState
import com.kavita.reader.core.designsystem.KavitaColors
import com.kavita.reader.core.designsystem.KavitaIcon
import com.kavita.reader.core.designsystem.KavitaIcons
import com.kavita.reader.core.designsystem.KavitaPullToRefreshBox
import com.kavita.reader.core.designsystem.KavitaTopAppBar
import com.kavita.reader.core.designsystem.RefreshablePlaceholder
import com.kavita.reader.core.designsystem.SeriesPosterCard
import com.kavita.reader.data.repository.ShelfCollection
import com.kavita.reader.ui.common.PrivacyToggle

/**
 * P12 锟?collections, reading lists and Want To Read.
 *
 * One composable serves all three: they are all "shelves", and the ViewModel already resolved
 * which endpoint backs this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    onOpenShelf: (Int) -> Unit,
    viewModel: ShelfViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Want To Read membership changes on the series detail screen; reloading on resume keeps this
    // list truthful when the user comes back (AGENTS.md §3 rule 5: optimistic + refreshable).
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { /* nothing to release */ }
    }

    val title = when {
        viewModel.isDetail -> stringResource(R.string.shelf_collection_detail)
        viewModel.shelfKind == ShelfKind.ReadingLists -> stringResource(R.string.shelf_reading_lists)
        viewModel.shelfKind == ShelfKind.WantToRead -> stringResource(R.string.shelf_want_to_read)
        else -> stringResource(R.string.shelf_collections)
    }

    Scaffold(
        topBar = {
            KavitaTopAppBar(
                title = { Text(title) },
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
                        title = when (viewModel.shelfKind) {
                            ShelfKind.WantToRead -> stringResource(R.string.shelf_want_to_read_empty)
                            ShelfKind.ReadingLists -> stringResource(R.string.shelf_lists_empty)
                            else -> stringResource(R.string.shelf_collections_empty)
                        },
                        body = stringResource(R.string.shelf_empty_body),
                        actionLabel = stringResource(R.string.common_retry),
                        onAction = viewModel::load,
                        illustration = R.drawable.empty_shelf,
                    )
                }

                // Series mode: collection contents or the Want To Read list.
                state.series.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.series, key = { it.summary.id }) { hit ->
                        SeriesPosterCard(
                            title = hit.summary.name,
                            coverUrl = hit.coverUrl,
                            subtitle = hit.summary.libraryName.takeIf { it.isNotBlank() },
                            progress = hit.summary.progress.takeIf { it > 0f },
                            onClick = { onOpenSeries(hit.summary.id) },
                        )
                    }
                }

                // Shelf mode: collections / reading lists.
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.shelves, key = { it.id }) { shelf ->
                        ShelfRow(
                            shelf = shelf,
                            showPromoted = viewModel.shelfKind != ShelfKind.WantToRead,
                            onClick = {
                                if (viewModel.shelfKind == ShelfKind.Collections) onOpenShelf(shelf.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfRow(
    shelf: ShelfCollection,
    showPromoted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(56.dp)) {
                CoverImage(
                    url = shelf.coverUrl,
                    contentDescription = shelf.title,
                    modifier = Modifier.fillMaxWidth().height(84.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = shelf.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (showPromoted && shelf.promoted) {
                        Surface(
                            color = KavitaColors.BrandPrimary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(start = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.shelf_promoted),
                                style = MaterialTheme.typography.labelSmall,
                                color = KavitaColors.BrandPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (!shelf.subtitle.isNullOrBlank()) {
                    Text(
                        text = shelf.subtitle!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(R.string.shelf_item_count, shelf.itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
