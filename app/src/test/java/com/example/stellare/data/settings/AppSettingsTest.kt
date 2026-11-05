package com.example.stellare.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `motyw systemowy z ciemnym systemem daje ciemny`() {
        assertTrue("SYSTEM przy ciemnym systemie ma dać ciemny motyw", ThemeChoice.SYSTEM.useDarkTheme(systemDark = true))
    }

    @Test
    fun `motyw systemowy z jasnym systemem daje jasny`() {
        assertFalse("SYSTEM przy jasnym systemie ma dać jasny motyw", ThemeChoice.SYSTEM.useDarkTheme(systemDark = false))
    }

    @Test
    fun `motyw jasny jest jasny niezaleznie od systemu`() {
        assertFalse("LIGHT przy ciemnym systemie ma zostać jasny", ThemeChoice.LIGHT.useDarkTheme(systemDark = true))
        assertFalse("LIGHT przy jasnym systemie ma zostać jasny", ThemeChoice.LIGHT.useDarkTheme(systemDark = false))
    }

    @Test
    fun `motyw ciemny jest ciemny niezaleznie od systemu`() {
        assertTrue("DARK przy jasnym systemie ma zostać ciemny", ThemeChoice.DARK.useDarkTheme(systemDark = false))
        assertTrue("DARK przy ciemnym systemie ma zostać ciemny", ThemeChoice.DARK.useDarkTheme(systemDark = true))
    }

    @Test
    fun `mnozniki czcionki dla trzech wartosci`() {
        assertEquals("NORMAL ma nie zmieniać skali", 1.0f, FontScaleChoice.NORMAL.multiplier, 0.0001f)
        assertEquals("LARGE ma powiększać o 15%", 1.15f, FontScaleChoice.LARGE.multiplier, 0.0001f)
        assertEquals("LARGEST ma powiększać o 30%", 1.3f, FontScaleChoice.LARGEST.multiplier, 0.0001f)
    }

    @Test
    fun `skala wypadkowa mnozy skale systemu`() {
        assertEquals("system 1,0 i LARGE", 1.15f, effectiveFontScale(1.0f, FontScaleChoice.LARGE), 0.0001f)
        assertEquals("system 1,2 i LARGEST", 1.56f, effectiveFontScale(1.2f, FontScaleChoice.LARGEST), 0.0001f)
    }

    @Test
    fun `normalny rozmiar nie zmienia skali systemu nawet poza zakresem`() {
        assertEquals("system 1,5 i NORMAL", 1.5f, effectiveFontScale(1.5f, FontScaleChoice.NORMAL), 0.0001f)
        assertEquals("system 2,4 i NORMAL nie może zostać obcięty", 2.4f, effectiveFontScale(2.4f, FontScaleChoice.NORMAL), 0.0001f)
    }

    @Test
    fun `skala wypadkowa nie przekracza przebadanego maksimum`() {
        assertEquals("system 2,0 i LARGEST ma zostać na 2,0", 2.0f, effectiveFontScale(2.0f, FontScaleChoice.LARGEST), 0.0001f)
        assertEquals("system 1,7 i LARGEST ma zostać na 2,0", 2.0f, effectiveFontScale(1.7f, FontScaleChoice.LARGEST), 0.0001f)
    }

    @Test
    fun `nieznany motyw z magazynu daje domyslny a nie wyjatek`() {
        assertEquals("nieznana nazwa motywu", ThemeChoice.SYSTEM, ThemeChoice.fromStored("NEON"))
        assertEquals("pusty napis", ThemeChoice.SYSTEM, ThemeChoice.fromStored(""))
        assertEquals("brak wartości", ThemeChoice.SYSTEM, ThemeChoice.fromStored(null))
    }

    @Test
    fun `nieznany rozmiar z magazynu daje domyslny a nie wyjatek`() {
        assertEquals("nieznana nazwa rozmiaru", FontScaleChoice.NORMAL, FontScaleChoice.fromStored("HUGE"))
        assertEquals("brak wartości", FontScaleChoice.NORMAL, FontScaleChoice.fromStored(null))
    }

    @Test
    fun `znane wartosci z magazynu sa odczytywane`() {
        assertEquals(ThemeChoice.DARK, ThemeChoice.fromStored("DARK"))
        assertEquals(ThemeChoice.LIGHT, ThemeChoice.fromStored("LIGHT"))
        assertEquals(FontScaleChoice.LARGE, FontScaleChoice.fromStored("LARGE"))
        assertEquals(FontScaleChoice.LARGEST, FontScaleChoice.fromStored("LARGEST"))
    }

    @Test
    fun `ustawienia domyslne to motyw systemowy i normalny rozmiar`() {
        assertEquals(AppSettings(ThemeChoice.SYSTEM, FontScaleChoice.NORMAL), AppSettings())
    }
}
