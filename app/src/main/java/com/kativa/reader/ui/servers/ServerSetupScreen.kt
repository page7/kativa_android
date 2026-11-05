package com.kativa.reader.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kativa.reader.R
import com.kativa.reader.core.designsystem.KativaIcon
import com.kativa.reader.core.designsystem.KativaIcons
import com.kativa.reader.core.designsystem.KativaColors
import com.kativa.reader.core.designsystem.KativaPrimaryButton
import com.kativa.reader.core.designsystem.KativaSecondaryButton
import com.kativa.reader.core.util.ServerUrl
import com.kativa.reader.ui.common.message

/**
 * P00B — add / edit a server. Cleartext is a per-server opt-in, and the startup auto-login
 * checkbox lives here because this is the moment the user is thinking about that server
 * (docs/PAGES.md P00B).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSetupScreen(
    onBack: () -> Unit,
    onSaved: (serverId: String, autoLogin: Boolean, probed: Boolean) -> Unit,
    viewModel: ServerSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.editingExisting) R.string.setup_title_edit else R.string.setup_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        KativaIcon(icon = KativaIcons.Back, contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.setup_name_label)) },
                placeholder = { Text(stringResource(R.string.setup_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::onUrlChange,
                label = { Text(stringResource(R.string.setup_url_label)) },
                placeholder = { Text(stringResource(R.string.setup_url_hint)) },
                singleLine = true,
                isError = state.error != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { reason ->
                Text(
                    text = when (reason) {
                        ServerUrl.Reason.Empty -> stringResource(R.string.setup_url_label)
                        ServerUrl.Reason.Malformed,
                        ServerUrl.Reason.MissingHost,
                        -> stringResource(R.string.error_dns)

                        ServerUrl.Reason.UnsupportedScheme -> stringResource(R.string.error_not_kavita)
                        ServerUrl.Reason.BlockedLoopback -> stringResource(R.string.error_dns)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            state.failure?.let { failure ->
                Text(
                    text = failure.error.message(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.testedOk) {
                Text(
                    text = state.testedVersion?.let {
                        stringResource(R.string.setup_version_ok, it)
                    } ?: stringResource(R.string.setup_version_ok_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = KativaColors.BrandPrimary,
                )
            }

            // Explain an automatic scheme downgrade instead of silently changing the user's input.
            if (state.autoSwitchedToCleartext) {
                Text(
                    text = stringResource(R.string.setup_switched_to_http),
                    style = MaterialTheme.typography.bodySmall,
                    color = KativaColors.Warning,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setup_allow_cleartext),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.setup_allow_cleartext_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Switch(
                    checked = state.allowCleartext,
                    onCheckedChange = viewModel::onAllowCleartextChange,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.setup_auto_login),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.autoLogin,
                    onCheckedChange = viewModel::onAutoLoginChange,
                )
            }

            Spacer(Modifier.height(8.dp))

            KativaSecondaryButton(
                text = stringResource(R.string.common_test_connection),
                onClick = viewModel::test,
                modifier = Modifier.fillMaxWidth(),
            )

            KativaPrimaryButton(
                text = stringResource(R.string.setup_save_and_login),
                onClick = { viewModel.save(onSaved) },
                enabled = state.canSave,
                loading = state.testing,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // Make the next step explicit: the previous build left users on this screen with no
            // visible way to enter credentials (docs/PAGES.md P00B).
            Text(
                text = stringResource(R.string.setup_next_step),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
