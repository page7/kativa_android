package com.kativa.reader.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kativa.reader.R
import kotlinx.coroutines.delay

/**
 * P00 Splash — logo only (no illustration, no tagline), then routes per SplashViewModel.
 */
@Composable
fun SplashScreen(
    onWelcome: () -> Unit,
    onServerPicker: () -> Unit,
    onHome: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        delay(500)
        viewModel.resolve()
    }

    LaunchedEffect(destination) {
        when (destination) {
            SplashDestination.Welcome -> onWelcome()
            SplashDestination.ServerPicker -> onServerPicker()
            SplashDestination.Home -> onHome()
            SplashDestination.Pending -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // The window background (res/values/themes.xml) is already the dark brand backdrop,
            // which is what covers the gap before Compose draws its first frame. Here it must
            // follow the *selected* theme instead: a hard-coded dark fill looked like a different
            // app on light theme.
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.logo_full),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(160.dp),
            )
            CircularProgressIndicator(
                modifier = Modifier.size(200.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                strokeWidth = 4.dp,
            )
        }
    }
}
