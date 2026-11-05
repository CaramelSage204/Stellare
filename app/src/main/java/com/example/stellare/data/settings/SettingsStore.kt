package com.example.stellare.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun setTheme(theme: ThemeChoice)
    suspend fun setFontScale(fontScale: FontScaleChoice)
    suspend fun setBubbleColor(bubbleColor: BubbleColorChoice)
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DataStoreSettingsStore.FILE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppSettings(
                theme = ThemeChoice.fromStored(preferences[themeKey]),
                fontScale = FontScaleChoice.fromStored(preferences[fontScaleKey]),
                bubbleColor = BubbleColorChoice.fromStored(preferences[bubbleColorKey])
            )
        }

    override suspend fun setTheme(theme: ThemeChoice) {
        dataStore.edit { it[themeKey] = theme.name }
    }

    override suspend fun setFontScale(fontScale: FontScaleChoice) {
        dataStore.edit { it[fontScaleKey] = fontScale.name }
    }

    override suspend fun setBubbleColor(bubbleColor: BubbleColorChoice) {
        dataStore.edit { it[bubbleColorKey] = bubbleColor.name }
    }

    companion object {
        internal const val FILE_NAME = "stellare_settings"
        internal const val THEME_KEY = "theme"
        internal const val FONT_SCALE_KEY = "font_scale"
        internal const val BUBBLE_COLOR_KEY = "bubble_color"

        private val themeKey = stringPreferencesKey(THEME_KEY)
        private val fontScaleKey = stringPreferencesKey(FONT_SCALE_KEY)
        private val bubbleColorKey = stringPreferencesKey(BUBBLE_COLOR_KEY)

        fun create(context: Context): DataStoreSettingsStore =
            DataStoreSettingsStore(context.applicationContext.settingsDataStore)

        internal fun createDataStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { file }
            )
    }
}
