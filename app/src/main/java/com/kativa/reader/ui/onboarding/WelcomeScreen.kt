package com.kativa.reader.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kativa.reader.R
import com.kativa.reader.core.designsystem.KativaColors
import kotlinx.coroutines.delay

/**
 * P00A Welcome — logo only.
 *
 * Explicit product rule: no illustration, no value proposition, no carousel.
 * The user gets 1.2s of brand, then taps (or is moved on automatically) to server setup
 * (docs/PAGES.md P00A, AGENTS.md §1).
 */
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(1200)
        onContinue()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KativaColors.Background)
            .clickable { onContinue() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.logo_full),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(180.dp),
        )
    }
}
