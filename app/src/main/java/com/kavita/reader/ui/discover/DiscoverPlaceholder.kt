package com.kavita.reader.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kavita.reader.R
import com.kavita.reader.core.designsystem.KavitaColors
import com.kavita.reader.core.designsystem.KavitaIcon
import com.kavita.reader.core.designsystem.KavitaIcons
import com.kavita.reader.core.designsystem.KavitaTopAppBar
import com.kavita.reader.core.designsystem.SectionHeader

/** P12 �?discovery entry points: search, collections, reading lists, want to read. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverPlaceholder(
    onOpenSearch: () -> Unit = {},
    onOpenCollections: () -> Unit = {},
    onOpenReadingLists: () -> Unit = {},
    onOpenWantToRead: () -> Unit = {},
) {
    Scaffold(
        topBar = { KavitaTopAppBar(title = { Text(stringResource(R.string.nav_discover)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(title = stringResource(R.string.discover_search_section))

            Surface(
                onClick = onOpenSearch,
                shape = RoundedCornerShape(12.dp),
                // Brand mint, not the deep green: this is a filled card, i.e. the same visual role
                // as a primary button (AGENTS.md §6).
                color = KavitaColors.BrandPrimary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KavitaIcon(
                        icon = KavitaIcons.Search,
                        contentDescription = null,
                        tint = KavitaColors.OnBrand,
                    )
                    Text(
                        text = stringResource(R.string.search_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = KavitaColors.OnBrand,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.discover_more_section))

            DiscoverRow(
                label = stringResource(R.string.shelf_collections),
                icon = KavitaIcons.Library,
                onClick = onOpenCollections,
            )
            DiscoverRow(
                label = stringResource(R.string.shelf_reading_lists),
                icon = KavitaIcons.Library,
                onClick = onOpenReadingLists,
            )
            DiscoverRow(
                label = stringResource(R.string.shelf_want_to_read),
                icon = KavitaIcons.Bookmark,
                onClick = onOpenWantToRead,
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.discover_upcoming_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiscoverRow(
    label: String,
    icon: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KavitaIcon(
                icon = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
