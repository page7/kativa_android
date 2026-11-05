package com.kativa.reader.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kativa.reader.R
import com.kativa.reader.core.designsystem.KativaIcon
import com.kativa.reader.core.designsystem.KativaIcons
import com.kativa.reader.core.designsystem.CoverImage
import com.kativa.reader.core.designsystem.ErrorState
import com.kativa.reader.core.designsystem.KativaColors
import com.kativa.reader.core.designsystem.KativaDimens
import com.kativa.reader.core.designsystem.KativaPrimaryButton
import com.kativa.reader.core.designsystem.LocalPrivacyMode
import com.kativa.reader.domain.model.Chapter
import com.kativa.reader.domain.model.ContentFormat
import com.kativa.reader.domain.model.PublicationStatus
import com.kativa.reader.domain.model.SeriesDetail
import com.kativa.reader.domain.model.Volume
import com.kativa.reader.ui.common.message

/** P06 — series detail with collapsible volumes and a single, obvious primary action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    privacyMode: Boolean = LocalPrivacyMode.current,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.detail?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        KativaIcon(icon = KativaIcons.Back, contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            KativaIcon(icon = KativaIcons.More, contentDescription = stringResource(R.string.common_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.series_mark_read)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.markSeriesRead()
                                },
                            )
                        }
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

                state.detail != null -> {
                    val detail = state.detail!!
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item("hero") {
                            SeriesHero(
                                detail = detail,
                                coverUrl = state.coverUrl,
                                privacyMode = privacyMode,
                                primaryLabel = when {
                                    state.primaryActionIsReread -> stringResource(R.string.series_reread)
                                    state.primaryActionIsStart -> stringResource(R.string.series_start)
                                    else -> stringResource(R.string.series_continue)
                                },
                                onPrimary = {
                                    state.resumeChapterId?.let(onOpenChapter)
                                },
                            )
                        }

                        item("progress") {
                            SeriesProgress(detail)
                        }

                        item("volumes-header") {
                            Text(
                                text = stringResource(R.string.series_volumes),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }

                        items(detail.volumes, key = { "vol-${it.id}" }) { volume ->
                            VolumeSection(
                                volume = volume,
                                expanded = volume.id in state.expandedVolumeIds,
                                onToggle = { viewModel.toggleVolume(volume.id) },
                                onOpenChapter = onOpenChapter,
                                onMarkChapter = viewModel::markChapterRead,
                                onMarkVolume = { viewModel.markVolumeRead(volume.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesHero(
    detail: SeriesDetail,
    coverUrl: String?,
    privacyMode: Boolean,
    primaryLabel: String,
    onPrimary: () -> Unit,
) {
    val accent = runCatching {
        Color(android.graphics.Color.parseColor(detail.primaryColor ?: "#2D7A56"))
    }.getOrElse { KativaColors.BrandPrimaryDeep }

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.55f), Color.Transparent),
                ),
            )
            .padding(16.dp),
    ) {
        Row {
            CoverImage(
                url = coverUrl,
                contentDescription = detail.name,
                privacyMode = privacyMode,
                modifier = Modifier.width(120.dp).aspectRatio(KativaDimens.PosterAspect),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = detail.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = listOfNotNull(
                        formatLabel(detail.format),
                        detail.libraryName.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.series_read_progress,
                        detail.readChapters,
                        detail.totalChapters,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                KativaPrimaryButton(text = primaryLabel, onClick = onPrimary)
            }
        }
    }
}

@Composable
private fun SeriesProgress(detail: SeriesDetail) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        LinearProgressIndicator(
            progress = { detail.progress },
            color = KativaColors.BrandPrimary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.series_read_progress, detail.readChapters, detail.totalChapters),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VolumeSection(
    volume: Volume,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onMarkChapter: (Chapter, Boolean) -> Unit,
    onMarkVolume: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KativaIcon(icon = if (expanded) KativaIcons.ExpandLess else KativaIcons.ExpandMore, contentDescription = null, tint =  MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = volume.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.series_volume_progress,
                        volume.pagesRead,
                        volume.pages,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (volume.isRead) {
                KativaIcon(icon = KativaIcons.Read, contentDescription = stringResource(R.string.series_read), tint =  KativaColors.BrandPrimary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = onMarkVolume) {
                KativaIcon(icon = KativaIcons.Read, contentDescription = stringResource(R.string.series_mark_read), tint =  MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (expanded) {
            volume.chapters.forEach { chapter ->
                ChapterRow(
                    chapter = chapter,
                    onClick = { onOpenChapter(chapter.id) },
                    onToggleRead = { onMarkChapter(chapter, !chapter.isRead) },
                )
            }
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: Chapter,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 40.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = chapter.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.series_chapter_pages, chapter.pages),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (chapter.isSpecial) {
                Text(
                    text = stringResource(R.string.series_special),
                    style = MaterialTheme.typography.labelMedium,
                    color = KativaColors.BrandPrimary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            IconButton(onClick = onToggleRead) {
                KativaIcon(icon = if (chapter.isRead) {
                        KativaIcons.Read
                    } else {
                        KativaIcons.Unread
                    }, contentDescription = stringResource(
                        if (chapter.isRead) R.string.series_mark_chapter_unread
                        else R.string.series_mark_chapter_read,
                    ),
                    tint = if (chapter.isRead) KativaColors.BrandPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun formatLabel(format: ContentFormat): String = when (format) {
    ContentFormat.Image, ContentFormat.Archive, ContentFormat.Unknown -> stringResource(R.string.format_manga)
    ContentFormat.Epub -> stringResource(R.string.format_book)
    ContentFormat.Pdf -> stringResource(R.string.format_pdf)
}

@Composable
private fun statusLabel(status: PublicationStatus): String = when (status) {
    PublicationStatus.OnGoing -> stringResource(R.string.status_ongoing)
    PublicationStatus.Completed -> stringResource(R.string.status_completed)
    PublicationStatus.Hiatus -> stringResource(R.string.status_hiatus)
    PublicationStatus.Cancelled -> stringResource(R.string.status_cancelled)
    PublicationStatus.Ended -> stringResource(R.string.status_ended)
    PublicationStatus.Unknown -> ""
}
