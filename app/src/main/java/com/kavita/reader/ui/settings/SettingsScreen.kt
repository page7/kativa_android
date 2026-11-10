package com.kavita.reader.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavita.reader.R
import com.kavita.reader.core.designsystem.KavitaColors
import com.kavita.reader.core.designsystem.KavitaIcon
import com.kavita.reader.core.designsystem.KavitaIcons
import com.kavita.reader.core.designsystem.KavitaSwitch
import com.kavita.reader.core.designsystem.KavitaTopAppBar
import com.kavita.reader.core.designsystem.ThemeMode
import com.kavita.reader.ui.reader.ReaderSettingsSheet

/** P15 — settings groups: startup, servers, appearance, language, about. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showReaderDefaults by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            KavitaTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .verticalScroll(rememberScrollState()),
        ) {
            // Startup and server management deliberately live on the Me screen: duplicating them
            // here created two entry points for the same actions with different wording.
            GroupTitle(stringResource(R.string.settings_group_appearance))

            SettingRow(
                title = stringResource(R.string.settings_theme),
                subtitle = when (state.themeMode) {
                    ThemeMode.System -> stringResource(R.string.settings_theme_system)
                    ThemeMode.Dark -> stringResource(R.string.settings_theme_dark)
                    ThemeMode.Light -> stringResource(R.string.settings_theme_light)
                },
                onClick = { showThemeDialog = true },
            )

            SettingRow(
                title = stringResource(R.string.settings_privacy_mode),
                subtitle = stringResource(R.string.settings_privacy_mode_desc),
                trailing = {
                    KavitaSwitch(
                        checked = state.privacyMode,
                        onCheckedChange = viewModel::setPrivacyMode,
                    )
                },
            )

            SettingRow(
                title = stringResource(R.string.settings_language),
                subtitle = languageLabel(state.language),
                onClick = { showLanguageDialog = true },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            GroupTitle(stringResource(R.string.settings_group_reader))
            SettingRow(
                title = stringResource(R.string.settings_reader_defaults),
                subtitle = stringResource(R.string.settings_reader_defaults_desc),
                onClick = { showReaderDefaults = true },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            GroupTitle(stringResource(R.string.settings_group_about))
            SettingRow(
                title = stringResource(R.string.settings_version),
                subtitle = state.versionName,
            )
            SettingRow(
                title = stringResource(R.string.settings_logout),
                onClick = { showLogoutDialog = true },
                titleColor = KavitaColors.Danger,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showReaderDefaults) {
        // Reuses the reader drawer so the two surfaces can never drift apart. Global mode:
        // no per-series reset (the global layer *is* the fallback) and no brightness row
        // (brightness is session-only and never leaves the reader).
        ReaderSettingsSheet(
            settings = state.readerSettings,
            title = stringResource(R.string.settings_reader_defaults),
            showBrightness = false,
            onDismiss = { showReaderDefaults = false },
            onReadingDirection = { viewModel.updateReaderDefaults { s -> s.copy(readingDirection = it) } },
            onReaderMode = { viewModel.updateReaderDefaults { s -> s.copy(readerMode = it) } },
            onPageLayout = { viewModel.updateReaderDefaults { s -> s.copy(layoutMode = it) } },
            onScaling = { viewModel.updateReaderDefaults { s -> s.copy(scalingOption = it) } },
            onCoverOffset = { viewModel.updateReaderDefaults { s -> s.copy(coverOffset = it) } },
            onBrightness = {},
            onShowPageNumber = { viewModel.updateReaderDefaults { s -> s.copy(showPageNumber = it) } },
            onVolumeKeyPaging = { viewModel.updateReaderDefaults { s -> s.copy(volumeKeyPaging = it) } },
            onForceLandscape = { viewModel.updateReaderDefaults { s -> s.copy(forceLandscape = it) } },
        )
    }

    if (showThemeDialog) {
        SelectionDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                ThemeMode.System to stringResource(R.string.settings_theme_system),
                ThemeMode.Dark to stringResource(R.string.settings_theme_dark),
                ThemeMode.Light to stringResource(R.string.settings_theme_light),
            ),
            selected = state.themeMode,
            onSelect = { viewModel.setThemeMode(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showLanguageDialog) {
        val options = listOf(
            "" to stringResource(R.string.settings_language_system),
            "zh" to "中文",
            "en" to "English",
            "fr" to "Français",
            "ja" to "日本語",
        )
        SelectionDialog(
            title = stringResource(R.string.settings_language),
            options = options,
            selected = state.language.orEmpty(),
            onSelect = { tag ->
                viewModel.setLanguage(tag.ifBlank { null })
                AppCompatDelegate.setApplicationLocales(
                    if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(tag),
                )
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.settings_logout_title)) },
            text = { Text(stringResource(R.string.settings_logout_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.signOutCurrent(onSignedOut)
                }) { Text(stringResource(R.string.settings_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = KavitaColors.BrandPrimary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun <T> SelectionDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}

@Composable
private fun languageLabel(tag: String?): String = when (tag) {
    null, "" -> stringResource(R.string.settings_language_system)
    "zh" -> "中文"
    "en" -> "English"
    "fr" -> "Français"
    "ja" -> "日本語"
    else -> tag
}
