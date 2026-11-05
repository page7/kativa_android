package com.kativa.reader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kativa.reader.R
import com.kativa.reader.core.designsystem.KativaColors
import com.kativa.reader.core.designsystem.SegmentedChoice
import com.kativa.reader.domain.model.PageLayout
import com.kativa.reader.domain.model.ReaderMode
import com.kativa.reader.domain.model.ReadingDirection
import com.kativa.reader.domain.model.ScalingOption

/**
 * Reader settings sheet (docs/PAGES.md P08).
 *
 * Only options the mobile reader actually implements are exposed. Anything from the server
 * reading profile that is not honoured here is omitted rather than shown as a dead switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onDismiss: () -> Unit,
    onReadingDirection: (ReadingDirection) -> Unit,
    onReaderMode: (ReaderMode) -> Unit,
    onPageLayout: (PageLayout) -> Unit,
    onScaling: (ScalingOption) -> Unit,
    onCoverOffset: (Boolean) -> Unit,
    onBrightness: (Float) -> Unit,
    onShowPageNumber: (Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_settings),
                style = MaterialTheme.typography.titleMedium,
            )

            SettingBlock(stringResource(R.string.reader_setting_direction)) {
                SegmentedChoice(
                    options = listOf(
                        ReadingDirection.LeftToRight to stringResource(R.string.reader_direction_ltr),
                        ReadingDirection.RightToLeft to stringResource(R.string.reader_direction_rtl),
                    ),
                    selected = settings.readingDirection,
                    onSelect = onReadingDirection,
                )
            }

            SettingBlock(stringResource(R.string.reader_setting_mode)) {
                SegmentedChoice(
                    options = listOf(
                        ReaderMode.LeftRight to stringResource(R.string.reader_mode_horizontal),
                        ReaderMode.UpDown to stringResource(R.string.reader_mode_vertical),
                        ReaderMode.Webtoon to stringResource(R.string.reader_mode_webtoon),
                    ),
                    selected = settings.readerMode,
                    onSelect = onReaderMode,
                )
            }

            if (!settings.isWebtoon) {
                SettingBlock(stringResource(R.string.reader_setting_layout)) {
                    SegmentedChoice(
                        options = listOf(
                            PageLayout.Single to stringResource(R.string.reader_layout_single),
                            PageLayout.Double to stringResource(R.string.reader_layout_double),
                            PageLayout.DoubleReversed to stringResource(R.string.reader_layout_double_manga),
                        ),
                        selected = settings.layoutMode,
                        onSelect = onPageLayout,
                    )
                }

                SettingBlock(stringResource(R.string.reader_setting_scale)) {
                    SegmentedChoice(
                        options = listOf(
                            ScalingOption.FitToHeight to stringResource(R.string.reader_scale_height),
                            ScalingOption.FitToWidth to stringResource(R.string.reader_scale_width),
                            ScalingOption.Original to stringResource(R.string.reader_scale_original),
                        ),
                        selected = settings.scalingOption,
                        onSelect = onScaling,
                    )
                }

                SwitchRow(
                    title = stringResource(R.string.reader_setting_cover_offset),
                    subtitle = stringResource(R.string.reader_setting_cover_offset_desc),
                    checked = settings.coverOffset,
                    onCheckedChange = onCoverOffset,
                )
            }

            SettingBlock(stringResource(R.string.reader_setting_brightness)) {
                Slider(
                    value = settings.brightness,
                    onValueChange = onBrightness,
                    valueRange = 0.2f..1f,
                )
            }

            SwitchRow(
                title = stringResource(R.string.reader_setting_page_number),
                subtitle = null,
                checked = settings.showPageNumber,
                onCheckedChange = onShowPageNumber,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingBlock(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = KativaColors.BrandPrimary,
        )
        content()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
