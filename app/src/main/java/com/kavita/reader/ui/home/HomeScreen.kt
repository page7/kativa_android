package com.kavita.reader.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavita.reader.R
import com.kavita.reader.core.designsystem.KavitaIcon
import com.kavita.reader.core.designsystem.KavitaIcons
import com.kavita.reader.core.designsystem.KavitaTopAppBar
import com.kavita.reader.core.designsystem.EmptyState
import com.kavita.reader.core.designsystem.KavitaPullToRefreshBox
import com.kavita.reader.core.designsystem.RefreshablePlaceholder
import com.kavita.reader.core.designsystem.SectionHeader
import com.kavita.reader.core.designsystem.SeriesPosterCard
import com.kavita.reader.ui.common.PrivacyToggle

/**
 * P03 锟?home. Top bar carries search + the privacy toggle; there is deliberately no
 * notification affordance anywhere (docs/PAGES.md P03).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSeries: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMe: () -> Unit,
    onOpenLibrary: (Int) -> Unit,
    onOpenStream: (streamKey: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            KavitaTopAppBar(
                title = {},
                navigationIcon = {
                    // User centre entry. The greeting text was removed by product decision: the
                    // username is not worth a permanent slot in the top bar (docs/PAGES.md P03).
                    IconButton(onClick = onOpenMe) {
                        KavitaIcon(
                            icon = KavitaIcons.Me,
                            contentDescription = stringResource(R.string.me_title),
                        )
                    }
                },
                actions = {
                    PrivacyToggle(
                        enabled = state.privacyMode,
                        onToggle = viewModel::setPrivacyMode,
                    )
                    IconButton(onClick = onOpenSearch) {
                        KavitaIcon(
                            icon = KavitaIcons.Search,
                            contentDescription = stringResource(R.string.search_title),
                        )
                    }
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
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                        actionLabel = stringResource(R.string.common_retry),
                        onAction = viewModel::load,
                        illustration = R.drawable.empty_shelf,
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    // 16dp outer gutter. Inner components must NOT add their own horizontal
                    // padding, or the gutters double to 32dp and the folders look inset compared
                    // with everything else on the screen.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Streams are horizontal rows, so each occupies a full-width line.
                    gridItems(
                        items = state.streams,
                        key = { it.key },
                        span = { GridItemSpan(maxLineSpan) },
                    ) { stream ->
                        StreamRow(
                            stream = stream,
                            coverFor = viewModel::seriesCover,
                            onOpenSeries = onOpenSeries,
                            onViewAll = { onOpenStream(stream.key) },
                        )
                    }

                    // Libraries as folder tiles: two per row, below the streams.
                    if (state.folders.isNotEmpty()) {
                        item(key = "libraries-header", span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(title = stringResource(R.string.home_libraries))
                        }
                        gridItems(state.folders, key = { "folder-${it.libraryId}" }) { folder ->
                            LibraryFolderCard(
                                folder = folder,
                                onClick = { onOpenLibrary(folder.libraryId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamRow(
    stream: HomeStream,
    coverFor: (Int) -> String?,
    onOpenSeries: (Int) -> Unit,
    onViewAll: () -> Unit,
) {
    Column {
        SectionHeader(
            title = stringResource(stream.titleRes),
            actionLabel = stringResource(R.string.home_view_all),
            onAction = onViewAll,
        )
        Spacer(Modifier.height(8.dp))

        LazyRow(
            // No horizontal contentPadding: the parent grid already applies the 16dp gutter.
            // Adding it here too produced the 32dp double inset this screen used to have.
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (stream) {
                is HomeStream.OnDeck -> items(stream.items, key = { "deck-${it.id}" }) { series ->
                    SeriesPosterCard(
                        title = series.localizedName ?: series.name,
                        coverUrl = coverFor(series.id),
                        subtitle = series.libraryName,
                        onClick = { onOpenSeries(series.id) },
                        modifier = Modifier.width(120.dp),
                    )
                }

                is HomeStream.RecentlyUpdated -> items(stream.items, key = { "upd-${it.seriesId}-${it.chapterId}" }) { grouped ->
                    SeriesPosterCard(
                        title = grouped.localizedSeriesName ?: grouped.seriesName,
                        coverUrl = coverFor(grouped.seriesId),
                        subtitle = stringResource(R.string.home_chapter_new, grouped.count.toString()),
                        onClick = { onOpenSeries(grouped.seriesId) },
                        modifier = Modifier.width(120.dp),
                    )
                }

                is HomeStream.RecentlyAdded -> items(stream.items, key = { "new-${it.id}" }) { series ->
                    SeriesPosterCard(
                        title = series.localizedName ?: series.name,
                        coverUrl = coverFor(series.id),
                        subtitle = series.libraryName,
                        onClick = { onOpenSeries(series.id) },
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }
    }
}
