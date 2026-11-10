package com.kavita.reader.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kavita.reader.R
import com.kavita.reader.core.designsystem.KavitaColors
import com.kavita.reader.core.designsystem.KavitaIcon
import com.kavita.reader.core.designsystem.KavitaIcons
import com.kavita.reader.core.designsystem.KavitaPrimaryButton
import com.kavita.reader.core.designsystem.KavitaUnderlineField
import com.kavita.reader.core.designsystem.SegmentedChoice
import com.kavita.reader.domain.model.SeriesFilter
import com.kavita.reader.domain.model.SeriesSort

/**
 * Filter + sort sheet shared by every series grid.
 *
 * Three controls only, on purpose (`docs/FEATURES.md` F-13): sort field, a series-name query and
 * the want-to-read flag. The previous version exposed the whole `SeriesFilterV2Dto` matrix
 * (16 fields × 12 comparisons × And/Or), and it was broken: name matching defaulted to
 * `Contains(5)`, which Kavita only accepts for list-shaped fields, so the server threw and the
 * grid came back empty. Keep this sheet on top of [SeriesFilter]'s tiny field set so an invalid
 * combination cannot be composed in the first place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    initial: SeriesFilter,
    onDismiss: () -> Unit,
    onApply: (SeriesFilter) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current

    var sort by remember { mutableStateOf(initial.sort) }
    var ascending by remember { mutableStateOf(initial.ascending) }
    var nameQuery by remember { mutableStateOf(initial.nameQuery) }
    var wantToReadOnly by remember { mutableStateOf(initial.wantToRead == true) }

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleMedium)

            // ---- sort ----
            Label(stringResource(R.string.filter_sort))
            SegmentedChoice(
                options = SeriesSort.entries.map { it to sortLabel(it) },
                selected = sort,
                onSelect = { picked ->
                    // Switching field adopts that field's natural direction (A→Z for names,
                    // newest first for dates) instead of carrying a stale one across, which
                    // made "Date added + Ascending" show the oldest series first.
                    if (picked != sort) ascending = picked.defaultAscending
                    sort = picked
                },
            )
            SegmentedChoice(
                options = listOf(
                    true to stringResource(R.string.filter_sort_asc),
                    false to stringResource(R.string.filter_sort_desc),
                ),
                selected = ascending,
                onSelect = { ascending = it },
            )

            // ---- name query ----
            Label(stringResource(R.string.filter_field_name))
            KavitaUnderlineField(
                value = nameQuery,
                onValueChange = { nameQuery = it },
                placeholder = stringResource(R.string.filter_name_hint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                trailingIcon = {
                    if (nameQuery.isNotEmpty()) {
                        IconButton(onClick = { nameQuery = "" }) {
                            KavitaIcon(
                                icon = KavitaIcons.Close,
                                contentDescription = stringResource(R.string.common_close),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- want to read ----
            Label(stringResource(R.string.filter_field_want_to_read))
            SegmentedChoice(
                options = listOf(
                    false to stringResource(R.string.filter_any),
                    true to stringResource(R.string.filter_want_to_read_only),
                ),
                selected = wantToReadOnly,
                onSelect = { wantToReadOnly = it },
            )

            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = {
                        sort = SeriesSort.SortName
                        ascending = SeriesSort.SortName.defaultAscending
                        nameQuery = ""
                        wantToReadOnly = false
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.filter_reset))
                }
                KavitaPrimaryButton(
                    text = stringResource(R.string.filter_apply),
                    onClick = {
                        focusManager.clearFocus()
                        onApply(
                            SeriesFilter.Default
                                .withSort(sort, ascending)
                                .withNameQuery(nameQuery)
                                .withWantToRead(wantToReadOnly.takeIf { it }),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = KavitaColors.BrandPrimary,
    )
}

@Composable
private fun sortLabel(sort: SeriesSort): String = when (sort) {
    SeriesSort.SortName -> stringResource(R.string.filter_sort_name)
    SeriesSort.CreatedDate -> stringResource(R.string.filter_sort_added)
    SeriesSort.ReadProgress -> stringResource(R.string.filter_sort_last_read)
}
