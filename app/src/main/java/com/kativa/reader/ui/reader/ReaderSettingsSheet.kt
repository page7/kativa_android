package com.kativa.reader.ui.reader

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kativa.reader.R
import com.kativa.reader.core.designsystem.KativaColors
import com.kativa.reader.core.designsystem.KativaIcon
import com.kativa.reader.core.designsystem.KativaIcons
import com.kativa.reader.core.designsystem.KativaSlider
import com.kativa.reader.core.designsystem.KativaSwitch
import com.kativa.reader.core.designsystem.SegmentedChoice
import com.kativa.reader.domain.model.PageLayout
import com.kativa.reader.domain.model.ReaderMode
import com.kativa.reader.domain.model.ReadingDirection
import com.kativa.reader.domain.model.ScalingOption
import kotlin.math.roundToInt

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
    onVolumeKeyPaging: (Boolean) -> Unit,
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

            SettingBlock(
                label = stringResource(R.string.reader_setting_mode),
                icon = KativaIcons.Webtoon,
            ) {
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

            // Reading direction is a *horizontal* notion: it mirrors tap zones, the pager order
            // and the order of a facing pair. Vertical paging and Webtoon always run top-to-bottom,
            // so the control is hidden there instead of pretending to do something.
            if (settings.readingDirectionApplies) {
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
            }

            // A facing pair needs pages side by side, so layout only exists in horizontal paging.
            if (settings.pageLayoutApplies) {
                SettingBlock(
                    label = stringResource(R.string.reader_setting_layout),
                    icon = KativaIcons.DoublePage,
                ) {
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
            }

            // Scaling applies to every mode except Webtoon, whose strip is width-bound by design.
            if (!settings.isWebtoon) {
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
            }

            // Cover offset only shifts the pairing, so it is meaningless without a spread.
            if (settings.isDoublePage) {
                SwitchRow(
                    title = stringResource(R.string.reader_setting_cover_offset),
                    subtitle = stringResource(R.string.reader_setting_cover_offset_desc),
                    checked = settings.coverOffset,
                    onCheckedChange = onCoverOffset,
                )
            }

            SettingBlock(
                label = stringResource(R.string.reader_setting_brightness),
                icon = KativaIcons.Brightness,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KativaSlider(
                        value = settings.brightness,
                        onValueChange = onBrightness,
                        valueRange = 0.2f..1f,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(
                            R.string.reader_brightness_percent,
                            (settings.brightness * 100).roundToInt(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }

            SwitchRow(
                title = stringResource(R.string.reader_setting_page_number),
                subtitle = null,
                checked = settings.showPageNumber,
                onCheckedChange = onShowPageNumber,
            )

            SwitchRow(
                title = stringResource(R.string.reader_setting_volume_keys),
                subtitle = stringResource(R.string.reader_setting_volume_keys_desc),
                checked = settings.volumeKeyPaging,
                onCheckedChange = onVolumeKeyPaging,
                icon = KativaIcons.Volume,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingBlock(
    label: String,
    @DrawableRes icon: Int? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                KativaIcon(
                    icon = icon,
                    contentDescription = null,
                    tint = KativaColors.BrandPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = KativaColors.BrandPrimary,
            )
        }
        content()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @DrawableRes icon: Int? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            KativaIcon(
                icon = icon,
                contentDescription = null,
                tint = KativaColors.BrandPrimary,
            )
            Spacer(Modifier.width(8.dp))
        }
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
        KativaSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
