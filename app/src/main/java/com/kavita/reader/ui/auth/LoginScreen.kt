package com.kavita.reader.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavita.reader.R
import com.kavita.reader.core.designsystem.KavitaIcon
import com.kavita.reader.core.designsystem.KavitaIcons
import com.kavita.reader.core.designsystem.KavitaPrimaryButton
import com.kavita.reader.core.designsystem.KavitaTopAppBar
import com.kavita.reader.core.network.KavitaError
import com.kavita.reader.ui.common.loginMessage

/** P02 — password or API key sign-in for one specific server. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val passwordFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // The account is already known, so put the cursor in the password field and raise the IME:
    // re-login should be "type password, done", not "hunt for the right field".
    LaunchedEffect(state.usernamePrefilled) {
        if (state.usernamePrefilled) {
            passwordFocus.requestFocus()
            keyboard?.show()
        }
    }

    Scaffold(
        topBar = {
            KavitaTopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        KavitaIcon(icon = KavitaIcons.Back, contentDescription = stringResource(R.string.common_back),
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.serverName.isNotBlank()) {
                Text(
                    text = state.serverName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            TabRow(
                selectedTabIndex = state.mode.ordinal,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Tab(
                    selected = state.mode == LoginMode.Password,
                    onClick = { viewModel.onModeChange(LoginMode.Password) },
                    text = { Text(stringResource(R.string.login_tab_password)) },
                )
                Tab(
                    selected = state.mode == LoginMode.ApiKey,
                    onClick = { viewModel.onModeChange(LoginMode.ApiKey) },
                    text = { Text(stringResource(R.string.login_tab_api_key)) },
                )
            }

            when (state.mode) {
                LoginMode.Password -> {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = viewModel::onUsernameChange,
                        label = { Text(stringResource(R.string.login_username)) },
                        singleLine = true,
                        // The account from last time is pre-filled and read-only-ish: the user only
                        // has to type a password. Tapping the field still allows switching account.
                        supportingText = if (state.usernamePrefilled) {
                            { Text(stringResource(R.string.login_remembered_account, state.username)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text(stringResource(R.string.login_password)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.submit { onSignedIn() } },
                        ),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                KavitaIcon(
                                    icon = if (passwordVisible) {
                                        KavitaIcons.VisibilityOff
                                    } else {
                                        KavitaIcons.Visibility
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                        // When the account came from storage the password is the only thing left
                        // to type, so focus it straight away.
                        modifier = Modifier.fillMaxWidth().then(
                            if (state.usernamePrefilled) {
                                Modifier.focusRequester(passwordFocus)
                            } else {
                                Modifier
                            },
                        ),
                    )
                }

                LoginMode.ApiKey -> {
                    OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = viewModel::onApiKeyChange,
                        label = { Text(stringResource(R.string.login_api_key)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.submit { onSignedIn() } },
                        ),
                        trailingIcon = {
                            TextButton(onClick = {
                                clipboard.getText()?.text?.let(viewModel::onApiKeyChange)
                            }) {
                                Text(stringResource(R.string.login_paste))
                            }
                        },
                        supportingText = {
                            Text(stringResource(R.string.login_api_key_help))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            state.failure?.let { failure ->
                val msg = when (val error = failure.error) {
                    is KavitaError.Unauthorized -> error.loginMessage()
                    else -> failure.error.loginMessage()
                }
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(4.dp))

            KavitaPrimaryButton(
                text = stringResource(R.string.login_submit),
                onClick = { viewModel.submit { onSignedIn() } },
                enabled = state.canSubmit,
                loading = state.submitting,
                modifier = Modifier.fillMaxWidth(),
            )

            state.serverVersion?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.login_server_version, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
