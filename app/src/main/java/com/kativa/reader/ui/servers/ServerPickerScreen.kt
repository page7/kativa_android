package com.kativa.reader.ui.servers

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kativa.reader.R
import com.kativa.reader.core.designsystem.EmptyState
import com.kativa.reader.core.designsystem.KativaColors
import com.kativa.reader.core.designsystem.KativaDimens
import com.kativa.reader.core.designsystem.KativaIcon
import com.kativa.reader.core.designsystem.KativaIcons
import com.kativa.reader.core.designsystem.KativaPrimaryButton
import com.kativa.reader.core.designsystem.KativaTopAppBar
import com.kativa.reader.domain.model.ServerProfile
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/**
 * P01 — the default startup screen. No automatic sign-in unless the user opted in
 * (AGENTS.md §1, docs/FLOWS.md F1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerPickerScreen(
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onOpenServer: (String, Boolean) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: ServerPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<ServerProfile?>(null) }

    Scaffold(
        topBar = {
            KativaTopAppBar(
                title = { Text(stringResource(R.string.servers_title)) },
                // Optional: this screen doubles as the app's startup destination, where there is
                // nothing to go back to. When reached from "switch server" it must be dismissible.
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            KativaIcon(
                                icon = KativaIcons.Back,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.isEmpty -> EmptyState(
                    title = stringResource(R.string.servers_empty_title),
                    body = stringResource(R.string.servers_empty_body),
                    actionLabel = stringResource(R.string.servers_add),
                    onAction = onAddServer,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.servers_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(state.items, key = { it.profile.id }) { item ->
                        ServerCard(
                            item = item,
                            isAutoLogin = item.profile.id == state.autoLoginServerId,
                            onClick = {
                                scope.launch {
                                    val signedIn = viewModel.select(item.profile.id)
                                    onOpenServer(item.profile.id, signedIn)
                                }
                            },
                            onEdit = { onEditServer(item.profile.id) },
                            onDelete = { pendingDelete = item.profile },
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        KativaPrimaryButton(
                            text = stringResource(R.string.servers_add),
                            onClick = onAddServer,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.servers_delete_title)) },
            text = { Text(stringResource(R.string.servers_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(profile.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ServerCard(
    item: ServerItemState,
    isAutoLogin: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(KativaDimens.CardRadius.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.profile.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isAutoLogin) {
                        Spacer(Modifier.size(8.dp))
                        AutoLoginBadge()
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.profile.baseUrl.removeSuffix("api/").trimEnd('/'),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(item.status)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = when (item.status) {
                            ServerStatus.SignedIn -> stringResource(R.string.servers_state_logged_in)
                            ServerStatus.NeedsLogin -> stringResource(R.string.servers_state_needs_login)
                            ServerStatus.Expired -> stringResource(R.string.servers_state_expired)
                            ServerStatus.Unreachable -> stringResource(R.string.servers_state_unreachable)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // A remembered account turns "sign in again" into "type your password".
                    item.rememberedUsername?.takeIf { it.isNotBlank() }?.let { user ->
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = user,
                            style = MaterialTheme.typography.labelMedium,
                            color = KativaColors.BrandPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = item.profile.lastUsedAt?.let {
                            stringResource(
                                R.string.servers_last_used,
                                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it)),
                            )
                        } ?: stringResource(R.string.servers_never_used),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            Box {
                IconButton(onClick = { menu = true }) {
                    KativaIcon(icon = KativaIcons.More, contentDescription = stringResource(R.string.common_more),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_edit)) },
                        leadingIcon = { KativaIcon(KativaIcons.Edit, null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete)) },
                        leadingIcon = { KativaIcon(KativaIcons.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoLoginBadge() {
    Surface(
        color = KativaColors.BrandPrimary.copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = stringResource(R.string.servers_set_auto_login_on),
            style = MaterialTheme.typography.labelMedium,
            color = KativaColors.BrandPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun StatusDot(status: ServerStatus) {
    val color = when (status) {
        ServerStatus.SignedIn -> KativaColors.BrandPrimary
        ServerStatus.NeedsLogin, ServerStatus.Expired -> KativaColors.Warning
        ServerStatus.Unreachable -> KativaColors.Danger
    }
    Surface(color = color, shape = RoundedCornerShape(50), modifier = Modifier.size(8.dp)) {}
}
