package com.example.stellare

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.data.settings.DataStoreSettingsStore
import com.example.stellare.di.AppContainer
import com.example.stellare.di.stellareViewModelFactory
import com.example.stellare.ui.settings.SettingsViewModel
import com.example.stellare.ui.shell.StellareShell
import com.example.stellare.ui.theme.StellareTheme

class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer(DataStoreSettingsStore.create(applicationContext)) }
    private val settingsViewModel: SettingsViewModel by viewModels { stellareViewModelFactory(container) }

    private var firstFrameReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        holdFirstFrameUntilSettingsApplied()
        setContent {
            val settings = settingsViewModel.settings.collectAsStateWithLifecycle().value
                ?: return@setContent
            val darkTheme = settings.theme.useDarkTheme(isSystemInDarkTheme())
            DisposableEffect(darkTheme) {
                val barStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                onDispose {}
            }
            SideEffect { firstFrameReady = true }
            StellareTheme(settings = settings) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StellareShell(container = container)
                }
            }
        }
    }

    private fun holdFirstFrameUntilSettingsApplied() {
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!firstFrameReady) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }
}
