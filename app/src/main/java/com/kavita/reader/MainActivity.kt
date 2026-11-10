package com.kavita.reader

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavita.reader.core.designsystem.KavitaTheme
import com.kavita.reader.ui.navigation.KavitaNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The window background (the frame painted before Compose draws) is a resource — see
        // values/themes.xml and values-night/colors.xml. KavitaApp aligns the resource
        // configuration with the user's stored theme, so both the system splash window and this
        // window already match; nothing to set here at runtime.
        enableEdgeToEdge()

        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val privacyMode by themeViewModel.privacyMode.collectAsStateWithLifecycle()

            KavitaTheme(themeMode = themeMode, privacyMode = privacyMode) {
                Box(
                    // Must come from the scheme, not a fixed token: a hard-coded dark fill showed
                    // through the system-bar strips and during transitions in the light theme.
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    KavitaNavHost(
                        themeMode = themeMode,
                        privacyMode = privacyMode,
                    )
                }
            }
        }
    }
}
