package com.kativa.reader.ui.shelf

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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kativa.reader.R
import com.kativa.reader.core.designsystem.CoverImage
import com.kativa.reader.core.designsystem.EmptyState
import com.kativa.reader.core.designsystem.ErrorState
import com.kativa.reader.core.designsystem.KativaColors
import com.kativa.reader.core.designsystem.KativaIcon
import com.kativa.reader.core.designsystem.KativaIcons
import com.kativa.reader.core.designsystem.SeriesPosterCard
import com.kativa.reader.data.repository.ShelfCollection
import com.kativa.reader.ui.common.PrivacyToggle
import com.kativa.reader.ui.common.illustration
import com.kativa.reader.ui.common.message

/**
 * P12 â€?collections, reading lists and Want To Read.
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

    val title = when {
        viewModel.isDetail -> stringResource(R.string.shelf_collection_detail)
        viewModel.shelfKind == ShelfKind.ReadingLists -> stringResource(R.string.shelf_reading_lists)
        viewModel.shelfKind == ShelfKind.WantToRead -> stringResource(R.string.shelf_want_to_read)
        else -> stringResource(R.string.shelf_collections)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        KativaIcon(
                            icon = KativaIcons.Back,
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

                state.error != null -> ErrorState(
                    message = state.error!!.message(),
                    retryLabel = stringResource(R.string.common_retry),
                    onRetry = viewModel::load,
                    illustration = state.error!!.illustration,
                )

                state.isEmpty -> EmptyState(
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
                            color = KativaColors.BrandPrimary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(start = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.shelf_promoted),
                                style = MaterialTheme.typography.labelSmall,
                                color = KativaColors.BrandPrimary,
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
