package com.kativa.reader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kativa.reader.R
import com.kativa.reader.core.designsystem.CoverImage
import com.kativa.reader.core.designsystem.KativaColors
import com.kativa.reader.core.designsystem.LocalDarkTheme
import com.kativa.reader.core.designsystem.LocalPrivacyMode
import com.kativa.reader.core.designsystem.PrivacyCoverPlaceholder

/**
 * Single spacing token for the folder mosaic: used for the panel inset *and* the cover gutters,
 * so the two can never drift apart in value.
 */
private val INSET = 4.dp

/**
 * A library as a folder tile: a translucent grey panel holding a 2x2 mosaic of its first four
 * covers in default sort order, so the library is recognisable without opening it.
 *
 * Layout contract: [INSET] is used twice — once as the gap between the covers and the panel edge,
 * and once as the gap between adjacent covers. One token keeps the whole top area on a single
 * rhythm, so the mosaic reads as "covers sitting inside a folder" rather than "covers filling a
 * box". Cover slots always reserve space, so a library with 1-3 series still looks like a folder.
 */
@Composable
fun LibraryFolderCard(
    folder: HomeLibraryFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = LocalPrivacyMode.current,
) {
    val dark = LocalDarkTheme.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        // Translucent grey veil: dark theme needs a white film, light theme a black one.
        color = if (dark) KativaColors.FolderSurfaceDark else KativaColors.FolderSurfaceLight,
        modifier = modifier,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    // The same inset as the gaps between covers: the mosaic shrinks by one step
                    // all the way around instead of bleeding to the panel edge.
                    .padding(INSET),
            ) {
                if (privacyMode) {
                    // One flat placeholder is enough: four grey squares add noise, and privacy
                    // mode must not request anything anyway.
                    Box(Modifier.fillMaxSize()) {
                        PrivacyCoverPlaceholder(
                            modifier = Modifier.fillMaxSize(),
                            description = null,
                        )
                    }
                } else {
                    FolderMosaic(covers = folder.previewCovers)
                }

                // Type badge, so "Manga" vs "Book" libraries read differently at a glance.
                if (folder.libraryType != 0) {
                    Surface(
                        color = KativaColors.BrandPrimary.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    ) {
                        Text(
                            text = libraryTypeLabel(folder.libraryType),
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color(0xFF07130E),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.home_folder_series_count, folder.seriesCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 2x2 mosaic with [INSET] gutters between the covers.
 *
 * Always renders four slots; missing covers become dim placeholders rather than leaving the tile
 * lopsided.
 */
@Composable
private fun FolderMosaic(covers: List<String?>) {
    val padded = List(4) { index -> covers.getOrNull(index) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(INSET)) {
        MosaicRow(padded[0], padded[1], Modifier.weight(1f))
        MosaicRow(padded[2], padded[3], Modifier.weight(1f))
    }
}

@Composable
private fun MosaicRow(left: String?, right: String?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(INSET)) {
        MosaicCell(left, Modifier.weight(1f))
        MosaicCell(right, Modifier.weight(1f))
    }
}

@Composable
private fun MosaicCell(url: String?, modifier: Modifier) {
    Box(modifier.fillMaxSize().clip(RoundedCornerShape(3.dp))) {
        if (url != null) {
            CoverImage(
                url = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Empty slot: subtle, not a broken-image icon.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Spacer(Modifier.fillMaxSize(0.35f).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)))
            }
        }
    }
}

@Composable
private fun libraryTypeLabel(type: Int): String = when (type) {
    0 -> stringResource(R.string.format_manga)
    1, 5 -> stringResource(R.string.format_comic)
    2, 4 -> stringResource(R.string.format_book)
    3 -> stringResource(R.string.format_pdf)
    else -> ""
}
