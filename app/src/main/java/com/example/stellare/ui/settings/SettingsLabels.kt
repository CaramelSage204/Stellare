package com.example.stellare.ui.settings

import com.example.stellare.data.settings.BubbleColorChoice
import com.example.stellare.data.settings.FontScaleChoice
import com.example.stellare.data.settings.ThemeChoice

fun ThemeChoice.label(): String = when (this) {
    ThemeChoice.SYSTEM -> "Zgodny z systemem"
    ThemeChoice.LIGHT -> "Jasny"
    ThemeChoice.DARK -> "Ciemny"
}

fun FontScaleChoice.label(): String = when (this) {
    FontScaleChoice.NORMAL -> "Normalny"
    FontScaleChoice.LARGE -> "Duży"
    FontScaleChoice.LARGEST -> "Największy"
}
fun BubbleColorChoice.label(): String = when (this) {
    BubbleColorChoice.PEACH -> "Brzoskwiniowy"
    BubbleColorChoice.GREEN -> "Zielony"
    BubbleColorChoice.BLUE -> "Niebieski"
    BubbleColorChoice.NEUTRAL -> "Neutralny"
}

