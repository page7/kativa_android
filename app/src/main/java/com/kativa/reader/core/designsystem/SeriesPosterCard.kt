package com.kativa.reader.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 2:3 poster card used across home, library grid and search.
 *
 * Covers go through [CoverImage], so privacy mode is honoured automatically.
 */
@Composable
fun SeriesPosterCard(
    title: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    onClick: (() -> Unit)? = null,
    badge: String? = null,
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        color = Color.Transparent,
        shape = RoundedCornerShape(KativaDimens.CardRadius.dp),
        modifier = modifier,
    ) {
        Box {
            CoverImage(
                url = coverUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxWidth().aspectRatio(KativaDimens.PosterAspect),
            )

            // Bottom gradient keeps titles readable on covers.
            //
            // The veil is theme-dependent (AGENTS.md ยง6): dark theme fades to black so white ink
            // pops, light theme fades to *white* so the title can be black โ€?a black veil in light
            // mode read as a grey smudge across every list card.
            val dark = LocalDarkTheme.current
            val scrim = if (dark) KativaColors.PosterScrimDark else KativaColors.PosterScrimLight
            val titleInk = if (dark) Color.White else Color.Black
            val subtitleInk = if (dark) Color.White.copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.72f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(KativaDimens.PosterAspect),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxSize()
                        .padding(top = 40.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, scrim),
                            ),
                        ),
                )
            }

            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = titleInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (progress != null && progress > 0f) {
                KativaProgressBar(
                    progress = progress,
                    height = 3.dp,
                    color = KativaColors.BrandPrimary,
                    trackColor = if (dark) {
                        Color.White.copy(alpha = 0.24f)
                    } else {
                        Color.Black.copy(alpha = 0.24f)
                    },
                    animated = false,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }

            if (badge != null) {
                Surface(
                    color = KativaColors.BrandPrimary,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF07130E),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
