package com.kativa.reader.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kativa.reader.R
import com.kativa.reader.core.designsystem.KativaColors
import com.kativa.reader.core.designsystem.SegmentedChoice
import com.kativa.reader.domain.model.Comparison
import com.kativa.reader.domain.model.SeriesFilter
import com.kativa.reader.domain.model.SeriesFilterField
import com.kativa.reader.domain.model.SeriesSort

/**
 * Filter + sort sheet shared by every series grid.
 *
 * The statement model maps 1:1 onto Kavita's `SeriesFilterV2Dto`, so nothing here has to guess
 * field or comparison integers (docs/API.md §12).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    initial: SeriesFilter,
    onDismiss: () -> Unit,
    onApply: (SeriesFilter) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var sort by remember { mutableStateOf(initial.sort) }
    var ascending by remember { mutableStateOf(initial.ascending) }
    var combination by remember { mutableStateOf(initial.combination) }
    var statements by remember { mutableStateOf(initial.statements) }

    // Draft row the user is composing.
    var field by remember { mutableStateOf(SeriesFilterField.SeriesName) }
    var comparison by remember { mutableStateOf(Comparison.Contains) }
    var value by remember { mutableStateOf("") }

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
                options = SeriesSort.entries.take(6).map { it to sortLabel(it) },
                selected = sort,
                onSelect = { sort = it },
            )
            SegmentedChoice(
                options = listOf(
                    true to stringResource(R.string.filter_sort_asc),
                    false to stringResource(R.string.filter_sort_desc),
                ),
                selected = ascending,
                onSelect = { ascending = it },
            )

            // ---- combination ----
            Label(stringResource(R.string.filter_match))
            SegmentedChoice(
                options = listOf(
                    SeriesFilter.Combination.And to stringResource(R.string.filter_match_all),
                    SeriesFilter.Combination.Or to stringResource(R.string.filter_match_any),
                ),
                selected = combination,
                onSelect = { combination = it },
            )

            // ---- active statements ----
            if (statements.isNotEmpty()) {
                Label(stringResource(R.string.filter_active))
                statements.forEachIndexed { index, statement ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${fieldLabel(statement.field)} ${comparisonLabel(statement.comparison)} ${statement.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { statements = statements.filterIndexed { i, _ -> i != index } }) {
                            Text(stringResource(R.string.common_delete))
                        }
                    }
                }
            }

            // ---- new statement ----
            Label(stringResource(R.string.filter_add_condition))
            SegmentedChoice(
                options = listOf(
                    SeriesFilterField.SeriesName to fieldLabel(SeriesFilterField.SeriesName),
                    SeriesFilterField.Writers to fieldLabel(SeriesFilterField.Writers),
                    SeriesFilterField.Genres to fieldLabel(SeriesFilterField.Genres),
                    SeriesFilterField.Tags to fieldLabel(SeriesFilterField.Tags),
                ),
                selected = field,
                onSelect = { picked ->
                    field = picked
                    comparison = defaultComparisonFor(picked)
                },
            )
            SegmentedChoice(
                options = listOf(
                    SeriesFilterField.PublicationStatus to fieldLabel(SeriesFilterField.PublicationStatus),
                    SeriesFilterField.AgeRating to fieldLabel(SeriesFilterField.AgeRating),
                    SeriesFilterField.ReleaseYear to fieldLabel(SeriesFilterField.ReleaseYear),
                    SeriesFilterField.Formats to fieldLabel(SeriesFilterField.Formats),
                ),
                selected = field,
                onSelect = { picked ->
                    field = picked
                    comparison = defaultComparisonFor(picked)
                },
            )
            SegmentedChoice(
                options = listOf(
                    SeriesFilterField.UserRating to fieldLabel(SeriesFilterField.UserRating),
                    SeriesFilterField.ReadProgress to fieldLabel(SeriesFilterField.ReadProgress),
                    SeriesFilterField.WantToRead to fieldLabel(SeriesFilterField.WantToRead),
                    SeriesFilterField.Languages to fieldLabel(SeriesFilterField.Languages),
                ),
                selected = field,
                onSelect = { picked ->
                    field = picked
                    comparison = defaultComparisonFor(picked)
                },
            )

            SegmentedChoice(
                options = Comparison.entries
                    .filter { it.allowedFor(field) }
                    .map { it to comparisonLabel(it) },
                selected = comparison,
                onSelect = { comparison = it },
            )

            if (comparison.needsValue) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.filter_value)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field.needsNumericValue()) KeyboardType.Number else KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = {
                    val effectiveValue = if (comparison.needsValue) value.trim() else ""
                    if (!comparison.needsValue || effectiveValue.isNotEmpty()) {
                        statements = statements + SeriesFilter.Statement(field, comparison, effectiveValue)
                        value = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.filter_add))
            }

            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = {
                        statements = emptyList()
                        value = ""
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.filter_reset))
                }
                Button(
                    onClick = {
                        onApply(
                            SeriesFilter(
                                statements = statements,
                                combination = combination,
                                sort = sort,
                                ascending = ascending,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.filter_apply))
                }
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
        color = KativaColors.BrandPrimary,
    )
}

@Composable
private fun sortLabel(sort: SeriesSort): String = when (sort) {
    SeriesSort.SortName -> stringResource(R.string.filter_sort_name)
    SeriesSort.CreatedDate -> stringResource(R.string.filter_sort_added)
    SeriesSort.LastModifiedDate -> stringResource(R.string.filter_sort_modified)
    SeriesSort.LastChapterAdded -> stringResource(R.string.filter_sort_chapter_added)
    SeriesSort.TimeToRead -> stringResource(R.string.filter_sort_time)
    SeriesSort.ReleaseYear -> stringResource(R.string.filter_sort_year)
    SeriesSort.ReadProgress -> stringResource(R.string.filter_sort_progress)
    SeriesSort.UserRating -> stringResource(R.string.filter_sort_rating)
    SeriesSort.UnreadChapterCount -> stringResource(R.string.filter_sort_unread)
    SeriesSort.Random -> stringResource(R.string.filter_sort_random)
}

@Composable
private fun fieldLabel(field: SeriesFilterField): String = when (field) {
    SeriesFilterField.SeriesName -> stringResource(R.string.filter_field_name)
    SeriesFilterField.Writers -> stringResource(R.string.filter_field_writer)
    SeriesFilterField.Genres -> stringResource(R.string.filter_field_genre)
    SeriesFilterField.Tags -> stringResource(R.string.filter_field_tag)
    SeriesFilterField.PublicationStatus -> stringResource(R.string.filter_field_status)
    SeriesFilterField.AgeRating -> stringResource(R.string.filter_field_age)
    SeriesFilterField.UserRating -> stringResource(R.string.filter_field_rating)
    SeriesFilterField.ReadProgress -> stringResource(R.string.filter_field_progress)
    SeriesFilterField.Formats -> stringResource(R.string.filter_field_format)
    SeriesFilterField.ReleaseYear -> stringResource(R.string.filter_field_year)
    SeriesFilterField.Languages -> stringResource(R.string.filter_field_language)
    SeriesFilterField.WantToRead -> stringResource(R.string.filter_field_want_to_read)
}

@Composable
private fun comparisonLabel(comparison: Comparison): String = when (comparison) {
    Comparison.Equal -> stringResource(R.string.filter_cmp_is)
    Comparison.NotEqual -> stringResource(R.string.filter_cmp_is_not)
    Comparison.Contains -> stringResource(R.string.filter_cmp_contains)
    Comparison.NotContains -> stringResource(R.string.filter_cmp_not_contains)
    Comparison.GreaterThan -> ">"
    Comparison.GreaterThanEqual -> "≥"
    Comparison.LessThan -> "<"
    Comparison.LessThanEqual -> "≤"
    Comparison.BeginsWith -> stringResource(R.string.filter_cmp_starts)
    Comparison.EndsWith -> stringResource(R.string.filter_cmp_ends)
    Comparison.IsEmpty -> stringResource(R.string.filter_cmp_empty)
    Comparison.IsNotEmpty -> stringResource(R.string.filter_cmp_not_empty)
    Comparison.IsInLast -> stringResource(R.string.filter_cmp_recent)
}

private fun defaultComparisonFor(field: SeriesFilterField): Comparison = when (field) {
    SeriesFilterField.SeriesName,
    SeriesFilterField.Writers,
    SeriesFilterField.Genres,
    SeriesFilterField.Tags,
    SeriesFilterField.Languages,
    -> Comparison.Contains

    else -> Comparison.Equal
}

private fun SeriesFilterField.needsNumericValue(): Boolean = when (this) {
    SeriesFilterField.UserRating,
    SeriesFilterField.ReadProgress,
    SeriesFilterField.ReleaseYear,
    -> true

    else -> false
}
