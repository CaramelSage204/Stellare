package com.example.stellare.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.settings.AppSettings
import com.example.stellare.data.settings.BubbleColorChoice
import com.example.stellare.data.settings.FontScaleChoice
import com.example.stellare.data.settings.SettingsStore
import com.example.stellare.data.settings.ThemeChoice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {

    val settings: StateFlow<AppSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setTheme(theme: ThemeChoice) {
        viewModelScope.launch { store.setTheme(theme) }
    }

    fun setFontScale(fontScale: FontScaleChoice) {
        viewModelScope.launch { store.setFontScale(fontScale) }
    }

    fun setBubbleColor(bubbleColor: BubbleColorChoice) {
        viewModelScope.launch { store.setBubbleColor(bubbleColor) }
    }
}
