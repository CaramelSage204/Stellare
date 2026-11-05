package com.example.stellare.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsStoreTest {

    private lateinit var file: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        file = File.createTempFile("settings", ".preferences_pb").also { it.delete() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    private fun dataStore(): DataStore<Preferences> = DataStoreSettingsStore.createDataStore(file, scope)

    @Test
    fun `pusty magazyn daje ustawienia domyslne`() = runBlocking {
        val store = DataStoreSettingsStore(dataStore())
        assertEquals("pusty magazyn", AppSettings(), store.settings.first())
    }

    @Test
    fun `zapisany motyw jest odczytany`() = runBlocking {
        val store = DataStoreSettingsStore(dataStore())
        store.setTheme(ThemeChoice.DARK)
        assertEquals("motyw po zapisie", ThemeChoice.DARK, store.settings.first().theme)
    }

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
    fun `zmiana rozmiaru nie rusza motywu`() = runBlocking {
        val store = DataStoreSettingsStore(InMemoryPreferences())
        store.setTheme(ThemeChoice.LIGHT)
        store.setFontScale(FontScaleChoice.LARGEST)
        assertEquals(
            "zapis rozmiaru nie może wyzerować motywu",
            AppSettings(ThemeChoice.LIGHT, FontScaleChoice.LARGEST),
            store.settings.first()
        )
    }

    @Test
    fun `ustawienia przezywaja ponowne otwarcie magazynu`() = runBlocking {
        DataStoreSettingsStore(dataStore()).setFontScale(FontScaleChoice.LARGE)
        scope.cancel()
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = DataStoreSettingsStore(DataStoreSettingsStore.createDataStore(file, secondScope))
            assertEquals("rozmiar po ponownym otwarciu", FontScaleChoice.LARGE, reopened.settings.first().fontScale)
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun `nieznane wartosci z dysku daja domyslne a nie wyjatek`() = runBlocking {
        val raw = dataStore()
        raw.edit {
            it[stringPreferencesKey(DataStoreSettingsStore.THEME_KEY)] = "NEON"
            it[stringPreferencesKey(DataStoreSettingsStore.FONT_SCALE_KEY)] = "HUGE"
        }
        assertEquals("wartości z innej wersji aplikacji", AppSettings(), DataStoreSettingsStore(raw).settings.first())
    }

    @Test
    fun `znana wartosc obok nieznanej zostaje zachowana`() = runBlocking {
        val raw = dataStore()
        raw.edit {
            it[stringPreferencesKey(DataStoreSettingsStore.THEME_KEY)] = "DARK"
            it[stringPreferencesKey(DataStoreSettingsStore.FONT_SCALE_KEY)] = "HUGE"
        }
        assertEquals(
            "poprawny motyw nie może przepaść przez błędny rozmiar",
            AppSettings(ThemeChoice.DARK, FontScaleChoice.NORMAL),
            DataStoreSettingsStore(raw).settings.first()
        )
    }

    @Test
    fun `uszkodzony plik daje ustawienia domyslne a nie wyjatek`() = runBlocking {
        file.writeBytes(byteArrayOf(0x7f, 0x13, 0x42, 0x00, 0x55, 0x01, 0x02, 0x03, 0x7a))
        val store = DataStoreSettingsStore(dataStore())
        assertEquals("uszkodzony plik ustawień", AppSettings(), store.settings.first())
    }
}
