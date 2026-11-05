package com.example.stellare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val MaterialTheme.coinGold: Color
    @Composable
    @ReadOnlyComposable
    get() = if (colorScheme.background.luminance() < 0.5f) CoinGoldDark else CoinGoldLight