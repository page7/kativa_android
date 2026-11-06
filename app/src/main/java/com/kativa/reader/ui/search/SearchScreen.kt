package com.kativa.reader.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kativa.reader.R
import com.kativa.reader.core.designsystem.CoverImage
import com.kativa.reader.core.designsystem.EmptyState
import com.kativa.reader.core.designsystem.ErrorState
import com.kativa.reader.core.designsystem.KativaColors
import com.kativa.reader.core.designsystem.KativaDimens
import com.kativa.reader.core.designsystem.KativaIcon
import com.kativa.reader.core.designsystem.KativaIcons
import com.kativa.reader.core.designsystem.SectionHeader
import com.kativa.reader.core.designsystem.SeriesPosterCard
import com.kativa.reader.data.repository.SearchHit
import com.kativa.reader.data.repository.SearchKind
import com.kativa.reader.data.repository.SearchRepository
import com.kativa.reader.ui.common.illustration
import com.kativa.reader.ui.common.message

/**
 * P11 — search.
 *
 * Debounced, grouped by entity type, with in-session history. Series hits render as a poster row
 * (that is how people recognise them); everything else is a compact list row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.submit() }),
                        trailingIcon = {
                            if (state.query.isNotBlank()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    KativaIcon(
                                        icon = KativaIcons.Close,
                                        contentDescription = stringResource(R.string.common_close),
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        KativaIcon(
                            icon = KativaIcons.Back,
                            contentDescription = stringResource(R.string.common_back),
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
                    onRetry = viewModel::retry,
                    illustration = state.error!!.illustration,
                )

                state.isEmptyResult -> EmptyState(
                    title = stringResource(R.string.search_empty_title, state.query.trim()),
                    body = stringResource(R.string.search_empty_body, state.query.trim()),
                    illustration = R.drawable.empty_search,
                )

                state.belowMinLength -> EmptyState(
                    title = stringResource(
                        R.string.search_min_length,
                        SearchRepository.MIN_QUERY_LENGTH,
                    ),
                )

                state.results == null -> SearchHistory(
                    history = history,
                    onSelect = viewModel::selectHistory,
                    onRemove = viewModel::removeHistory,
                    onClear = viewModel::clearHistory,
                )

                else -> SearchResultList(
                    results = state.results!!,
                    onOpenSeries = onOpenSeries,
                    coverFor = viewModel::coverFor,
                )
            }
        }
    }
}

@Composable
private fun SearchHistory(
    history: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.search_history_empty),
            body = stringResource(R.string.search_history_empty_body),
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.search_history),
            actionLabel = stringResource(R.string.search_history_clear),
            onAction = onClear,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(history) { entry ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onSelect(entry) },
                ) {
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultList(
    results: com.kativa.reader.data.repository.SearchResults,
    onOpenSeries: (Int) -> Unit,
    coverFor: (SearchHit) -> String?,
) {
    val listState = rememberLazyListState()

    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        results.groups.forEach { group ->
            item(key = "header-${group.kind}") {
                SectionHeader(
                    title = groupTitle(group.kind),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (group.kind == SearchKind.Series) {
                item(key = "row-${group.kind}") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(group.hits, key = { "s-${it.id}" }) { hit ->
                            SeriesPosterCard(
                                title = hit.title,
                                coverUrl = coverFor(hit),
                                subtitle = hit.subtitle,
                                onClick = { onOpenSeries(hit.id) },
                                modifier = Modifier.width(120.dp),
                            )
                        }
                    }
                }
            } else {
                items(group.hits, key = { "${group.kind}-${it.id}" }) { hit ->
                    ResultRow(hit = hit, coverUrl = coverFor(hit))
                }
            }
        }
    }
}

@Composable
private fun ResultRow(hit: SearchHit, coverUrl: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (coverUrl != null) {
            Box(Modifier.width(40.dp)) {
                CoverImage(
                    url = coverUrl,
                    contentDescription = hit.title,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = hit.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!hit.subtitle.isNullOrBlank()) {
                Text(
                    text = hit.subtitle!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun groupTitle(kind: SearchKind): String = when (kind) {
    SearchKind.Series -> stringResource(R.string.search_group_series)
    SearchKind.Collection -> stringResource(R.string.search_group_collections)
    SearchKind.ReadingList -> stringResource(R.string.search_group_lists)
    SearchKind.Person -> stringResource(R.string.search_group_people)
    SearchKind.Genre -> stringResource(R.string.search_group_genres)
    SearchKind.Tag -> stringResource(R.string.search_group_tags)
    SearchKind.Library -> stringResource(R.string.search_group_libraries)
}
