package com.kavita.reader.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavita.reader.R
import com.kavita.reader.core.designsystem.KavitaColors
import com.kavita.reader.core.designsystem.KavitaDimens
import com.kavita.reader.core.designsystem.KavitaIcon
import com.kavita.reader.core.designsystem.KavitaIcons
import com.kavita.reader.core.designsystem.KavitaTopAppBar

/** P14 — profile summary, server switching entry and settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onManageServers: () -> Unit,
    onOpenBookmarks: () -> Unit = {},
    onOpenWantToRead: () -> Unit = {},
    viewModel: MeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            KavitaTopAppBar(
                // Me is reachable as a pushed destination (home's user-centre icon), so it needs
                // an explicit way back. Relying on the system back gesture alone left users
                // stranded on this screen.
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        KavitaIcon(
                            icon = KavitaIcons.Back,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                title = { Text(stringResource(R.string.nav_me)) },
                // No actions block: the privacy toggle lives on the library/home surfaces, and
                // Settings is already a menu row on this screen below. Two entry points to the
                // same page was redundant.
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(KavitaDimens.CardRadius.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = state.username.ifBlank { stringResource(R.string.home_greeting_anonymous) },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = roleLabel(state.isAdmin, state.isReadOnly),
                        style = MaterialTheme.typography.labelMedium,
                        color = KavitaColors.BrandPrimary,
                    )
                    if (state.serverName.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.me_current_server, state.serverName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Surface(
                onClick = onManageServers,
                shape = RoundedCornerShape(KavitaDimens.CardRadius.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.common_switch_server),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = state.serverName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Reading shelves are pulled together here so "Me" is the single place a reader goes
            // for "what did I save?" (docs/PAGES.md P14 → 快捷入口). Both entries are reads, so a
            // read-only account keeps them; only the write affordances (reader bookmark toggle)
            // are hidden for that role (AGENTS.md §4.6 item 6).
            MeMenuRow(
                label = stringResource(R.string.bookmarks_title),
                icon = KavitaIcons.Bookmark,
                onClick = onOpenBookmarks,
            )

            MeMenuRow(
                label = stringResource(R.string.shelf_want_to_read),
                icon = KavitaIcons.Favorite,
                onClick = onOpenWantToRead,
            )

            Surface(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(KavitaDimens.CardRadius.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MeMenuRow(
    label: String,
    icon: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(KavitaDimens.CardRadius.dp),
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
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun roleLabel(isAdmin: Boolean, isReadOnly: Boolean): String = when {
    isAdmin -> stringResource(R.string.me_role_admin)
    isReadOnly -> stringResource(R.string.me_role_read_only)
    else -> stringResource(R.string.me_role_reader)
}

