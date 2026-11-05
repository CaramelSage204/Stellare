package com.example.stellare.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleColorSettingsTest {

    private class InMemoryPreferences : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    @Test
    fun `domyslne ustawienia maja brzoskwiniowe dymki`() {
        assertEquals("kolor dymków w AppSettings()", BubbleColorChoice.PEACH, AppSettings().bubbleColor)
    }

    @Test
    fun `nieznana lub brakujaca wartosc daje domyslny kolor`() {
        assertEquals("null", BubbleColorChoice.PEACH, BubbleColorChoice.fromStored(null))
        assertEquals("śmieci", BubbleColorChoice.PEACH, BubbleColorChoice.fromStored("MAGENTA"))
    }

    @Test
    fun `zapisany kolor jest odczytany`() = runBlocking {
        val store = DataStoreSettingsStore(InMemoryPreferences())
        BubbleColorChoice.entries.forEach { choice ->
            store.setBubbleColor(choice)
            assertEquals("kolor po zapisie", choice, store.settings.first().bubbleColor)
        }
    }

    @Test
    fun `zmiana koloru nie rusza motywu ani rozmiaru`() = runBlocking {
        val store = DataStoreSettingsStore(InMemoryPreferences())
        store.setTheme(ThemeChoice.DARK)
        store.setFontScale(FontScaleChoice.LARGEST)
        store.setBubbleColor(BubbleColorChoice.BLUE)
        assertEquals(
            "pozostałe ustawienia po zmianie koloru",
            AppSettings(ThemeChoice.DARK, FontScaleChoice.LARGEST, BubbleColorChoice.BLUE),
            store.settings.first()
        )
    }

    @Test
    fun `zmiana motywu nie rusza koloru`() = runBlocking {
        val store = DataStoreSettingsStore(InMemoryPreferences())
        store.setBubbleColor(BubbleColorChoice.GREEN)
        store.setTheme(ThemeChoice.LIGHT)
        assertEquals("kolor po zmianie motywu", BubbleColorChoice.GREEN, store.settings.first().bubbleColor)
    }

    @Test
    fun `uszkodzona wartosc w magazynie daje domyslny kolor a nie wyjatek`() = runBlocking {
        val prefs = InMemoryPreferences()
        prefs.edit { it[stringPreferencesKey(DataStoreSettingsStore.BUBBLE_COLOR_KEY)] = "MAGENTA" }
        assertEquals(
            "odczyt z nieznaną wartością",
            BubbleColorChoice.PEACH,
            DataStoreSettingsStore(prefs).settings.first().bubbleColor
        )
    }
}
