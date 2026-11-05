package com.example.stellare.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.stellare.data.settings.AppSettings
import com.example.stellare.data.settings.effectiveFontScale

@Composable
fun StellareTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val darkTheme = settings.theme.useDarkTheme(isSystemInDarkTheme())
    val systemDensity = LocalDensity.current
    val density = remember(systemDensity, settings.fontScale) {
        Density(
            density = systemDensity.density,
            fontScale = effectiveFontScale(systemDensity.fontScale, settings.fontScale)
        )
    }
    CompositionLocalProvider(LocalDensity provides density) {
        MyApplicationTheme(darkTheme = darkTheme, content = content)
    }
}
