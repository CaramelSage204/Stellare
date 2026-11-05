package com.example.stellare.ui.settings

import com.example.stellare.data.settings.AppSettings
import com.example.stellare.data.settings.BubbleColorChoice
import com.example.stellare.data.settings.FontScaleChoice
import com.example.stellare.data.settings.SettingsStore
import com.example.stellare.data.settings.ThemeChoice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private class FakeStore(private val gate: CompletableDeferred<Unit>? = null) : SettingsStore {
        private val state = MutableStateFlow(AppSettings())
        override val settings: Flow<AppSettings> = flow {
            gate?.await()
            state.collect { emit(it) }
        }

        override suspend fun setTheme(theme: ThemeChoice) {
            state.value = state.value.copy(theme = theme)
        }

        override suspend fun setFontScale(fontScale: FontScaleChoice) {
            state.value = state.value.copy(fontScale = fontScale)
        }

        override suspend fun setBubbleColor(bubbleColor: BubbleColorChoice) {
            state.value = state.value.copy(bubbleColor = bubbleColor)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `do wczytania ustawien stan jest pusty a nie domyslny`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val viewModel = SettingsViewModel(FakeStore(gate))
        assertNull("przed wczytaniem nie wolno udawać ustawień domyślnych, bo interfejs mignąłby złym motywem", viewModel.settings.value)
        gate.complete(Unit)
        assertEquals("po wczytaniu", AppSettings(), viewModel.settings.value)
    }

    @Test
    fun `zmiana motywu trafia do stanu`() = runTest {
        val viewModel = SettingsViewModel(FakeStore())
        viewModel.setTheme(ThemeChoice.DARK)
        assertEquals("motyw w stanie", ThemeChoice.DARK, viewModel.settings.value?.theme)
    }

    @Test
    fun `zmiana rozmiaru trafia do stanu i zachowuje motyw`() = runTest {
        val viewModel = SettingsViewModel(FakeStore())
        viewModel.setTheme(ThemeChoice.LIGHT)
        viewModel.setFontScale(FontScaleChoice.LARGE)
        assertEquals(
            "rozmiar w stanie obok motywu",
            AppSettings(ThemeChoice.LIGHT, FontScaleChoice.LARGE),
            viewModel.settings.value
        )
    }
}
