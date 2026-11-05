package com.kativa.reader

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kativa.reader.core.designsystem.KativaColors
import com.kativa.reader.core.designsystem.KativaTheme
import com.kativa.reader.ui.navigation.KativaNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val privacyMode by themeViewModel.privacyMode.collectAsStateWithLifecycle()

            KativaTheme(themeMode = themeMode, privacyMode = privacyMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(KativaColors.Background),
                ) {
                    KativaNavHost(
                        themeMode = themeMode,
                        privacyMode = privacyMode,
                    )
                }
            }
        }
    }
}
