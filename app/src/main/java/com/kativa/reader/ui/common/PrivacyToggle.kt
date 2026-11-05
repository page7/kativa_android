package com.kativa.reader.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kativa.reader.R
import com.kativa.reader.core.designsystem.KativaColors

/**
 * The privacy-mode switch shown in the top-right corner of every main screen.
 *
 * Visual state is the icon plus a tinted circular background, so it is obvious at a glance
 * (docs/PAGES.md, docs/FLOWS.md F3b).
 */
@Composable
fun PrivacyToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = { onToggle(!enabled) },
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(
                if (enabled) R.drawable.ic_privacy_on else R.drawable.ic_privacy_off,
            ),
            contentDescription = stringResource(R.string.privacy_toggle_desc),
            tint = if (enabled) KativaColors.BrandPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}
