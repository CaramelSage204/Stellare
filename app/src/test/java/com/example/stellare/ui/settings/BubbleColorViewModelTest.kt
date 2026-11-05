package com.example.stellare.ui.settings

import com.example.stellare.data.settings.AppSettings
import com.example.stellare.data.settings.BubbleColorChoice
import com.example.stellare.data.settings.FontScaleChoice
import com.example.stellare.data.settings.SettingsStore
import com.example.stellare.data.settings.ThemeChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BubbleColorViewModelTest {

    private class FakeStore : SettingsStore {
        private val state = MutableStateFlow(AppSettings())
        override val settings: Flow<AppSettings> = state

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
    fun `wybor koloru trafia do magazynu i do stanu`() = runTest {
        val viewModel = SettingsViewModel(FakeStore())
        viewModel.setBubbleColor(BubbleColorChoice.BLUE)
        assertEquals("kolor w stanie po wyborze", BubbleColorChoice.BLUE, viewModel.settings.value?.bubbleColor)
    }

    @Test
    fun `kazdy kolor ma inna polska nazwe`() {
        val labels = BubbleColorChoice.entries.map { it.label() }
        assertEquals("nazwy: $labels", BubbleColorChoice.entries.size, labels.toSet().size)
        assertEquals("brzoskwiniowy ma nazwę po polsku", "Brzoskwiniowy", BubbleColorChoice.PEACH.label())
    }
}
