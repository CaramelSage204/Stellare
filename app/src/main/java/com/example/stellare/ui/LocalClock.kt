package com.example.stellare.ui

import androidx.compose.runtime.staticCompositionLocalOf

val LocalClock = staticCompositionLocalOf<() -> Long> { System::currentTimeMillis }
