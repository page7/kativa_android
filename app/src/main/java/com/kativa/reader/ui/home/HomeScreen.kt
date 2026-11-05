package com.kativa.reader.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.kativa.reader.core.designsystem.KativaIcon
import com.kativa.reader.core.designsystem.KativaIcons
import com.kativa.reader.core.designsystem.CoverImage
import com.kativa.reader.core.designsystem.EmptyState
import com.kativa.reader.core.designsystem.ErrorState
import com.kativa.reader.core.designsystem.KativaColors
import com.kativa.reader.core.designsystem.KativaDimens
import com.kativa.reader.core.designsystem.SectionHeader
import com.kativa.reader.core.designsystem.SeriesPosterCard
import com.kativa.reader.ui.common.message
import com.kativa.reader.ui.common.PrivacyToggle

/**
 * P03 — home. Top bar carries search + the privacy toggle; there is deliberately no
 * notification affordance anywhere (docs/PAGES.md P03).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSeries: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (state.username.isBlank()) {
                                stringResource(R.string.home_greeting_anonymous)
                            } else {
                                stringResource(R.string.home_greeting, state.username)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    PrivacyToggle(
                        enabled = state.privacyMode,
                        onToggle = viewModel::setPrivacyMode,
                    )
                    IconButton(onClick = onOpenSearch) {
                        KativaIcon(icon = KativaIcons.Search, contentDescription = stringResource(R.string.home_view_all),
                        )
                    }
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
                )

                state.isEmpty -> EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = viewModel::load,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    state.continueItem?.let { cont ->
                        item(key = "continue-card") {
                            ContinueReadingCard(
                                series = cont.series,
                                coverUrl = viewModel.seriesCover(cont.series.id),
                                privacyMode = state.privacyMode,
                                onClick = { onOpenSeries(cont.series.id) },
                            )
                        }
                    }

                    items(state.streams.filterNot { it is HomeStream.ContinueReading }, key = { it.key }) { stream ->
                        StreamRow(
                            stream = stream,
                            privacyMode = state.privacyMode,
                            coverFor = viewModel::seriesCover,
                            onOpenSeries = onOpenSeries,
                            onViewAll = { onOpenSettings },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(
    series: com.kativa.reader.data.remote.dto.SeriesDto,
    coverUrl: String?,
    privacyMode: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(KativaDimens.CardRadius.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(72.dp)) {
                CoverImage(
                    url = coverUrl,
                    contentDescription = series.name,
                    modifier = Modifier.fillMaxWidth().aspectRatio(KativaDimens.PosterAspect),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_continue_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = KativaColors.BrandPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = series.localizedName ?: series.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.home_continue_page_of,
                        (series.pagesRead + 1).coerceAtMost(series.pages.coerceAtLeast(1)),
                        series.pages.coerceAtLeast(1),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StreamRow(
    stream: HomeStream,
    privacyMode: Boolean,
    coverFor: (Int) -> String?,
    onOpenSeries: (Int) -> Unit,
    onViewAll: () -> Unit,
) {
    Column {
        SectionHeader(
            title = stringResource(stream.titleRes),
            actionLabel = stringResource(R.string.home_view_all),
            onAction = onViewAll,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (stream) {
                is HomeStream.OnDeck -> items(stream.items, key = { "deck-${it.id}" }) { series ->
                    SeriesPosterCard(
                        title = series.localizedName ?: series.name,
                        coverUrl = if (privacyMode) null else coverFor(series.id),
                        subtitle = series.libraryName,
                        onClick = { onOpenSeries(series.id) },
                        modifier = Modifier.width(120.dp),
                    )
                }

                is HomeStream.RecentlyUpdated -> items(stream.items, key = { "upd-${it.seriesId}-${it.chapterId}" }) { grouped ->
                    SeriesPosterCard(
                        title = grouped.localizedSeriesName ?: grouped.seriesName,
                        coverUrl = if (privacyMode) null else coverFor(grouped.seriesId),
                        subtitle = stringResource(R.string.home_chapter_new, grouped.count.toString()),
                        onClick = { onOpenSeries(grouped.seriesId) },
                        modifier = Modifier.width(120.dp),
                    )
                }

                is HomeStream.RecentlyAdded -> items(stream.items, key = { "new-${it.id}" }) { series ->
                    SeriesPosterCard(
                        title = series.localizedName ?: series.name,
                        coverUrl = if (privacyMode) null else coverFor(series.id),
                        subtitle = series.libraryName,
                        onClick = { onOpenSeries(series.id) },
                        modifier = Modifier.width(120.dp),
                    )
                }

                is HomeStream.ContinueReading -> Unit
            }
        }
    }
}
