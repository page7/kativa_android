package com.kativa.reader.core.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Lightweight segmented control.
 *
 * Built here rather than pulled from Material so the look matches the design tokens and the
 * text scales correctly at large font sizes (AGENTS.md §6).
 */
@Composable
fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(4.dp)) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    // Selected segment uses the brand mint with the dark brand ink, not the deep
                    // green: on a filled chip the deep tone just reads as "darkened green"
                    // (AGENTS.md §6). White text on mint is ~2.2:1, hence `OnBrand`.
                    color = if (isSelected) KativaColors.BrandPrimary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = isSelected, onClick = { onSelect(value) }),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) KativaColors.OnBrand else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .padding(horizontal = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}
