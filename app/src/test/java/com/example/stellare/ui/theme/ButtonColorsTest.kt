package com.example.stellare.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonColorsTest {

    private fun contrast(foreground: Color, background: Color): Double {
        val flattened = foreground.compositeOver(background)
        val a = flattened.luminance().toDouble() + 0.05
        val b = background.luminance().toDouble() + 0.05
        return maxOf(a, b) / minOf(a, b)
    }

    private fun disabledContrast(scheme: ColorScheme) =
        contrast(scheme.disabledButtonContent(), scheme.disabledButtonContainer())

    private fun activeContrast(scheme: ColorScheme) = contrast(scheme.onPrimary, scheme.primary)

    @Test
    fun `wylaczony przycisk w trybie jasnym jest czytelny`() {
        val ratio = disabledContrast(LightColorScheme)
        assertTrue("tekst wyłączonego przycisku w trybie jasnym ma kontrast $ratio, wymagane co najmniej 3.0", ratio >= 3.0)
    }

    @Test
    fun `wylaczony przycisk w trybie ciemnym jest czytelny`() {
        val ratio = disabledContrast(DarkColorScheme)
        assertTrue("tekst wyłączonego przycisku w trybie ciemnym ma kontrast $ratio, wymagane co najmniej 3.0", ratio >= 3.0)
    }

    @Test
    fun `wylaczony przycisk jest slabszy od aktywnego w obu trybach`() {
        assertTrue(
            "wyłączony przycisk w trybie jasnym (${disabledContrast(LightColorScheme)}) ma być słabszy od aktywnego (${activeContrast(LightColorScheme)})",
            disabledContrast(LightColorScheme) < activeContrast(LightColorScheme)
        )
        assertTrue(
            "wyłączony przycisk w trybie ciemnym (${disabledContrast(DarkColorScheme)}) ma być słabszy od aktywnego (${activeContrast(DarkColorScheme)})",
            disabledContrast(DarkColorScheme) < activeContrast(DarkColorScheme)
        )
    }

    @Test
    fun `wylaczone tlo rozni sie od aktywnego`() {
        assertNotEquals(
            "tło wyłączonego przycisku w trybie jasnym nie może być takie samo jak aktywnego",
            LightColorScheme.primary,
            LightColorScheme.disabledButtonContainer()
        )
        assertNotEquals(
            "tło wyłączonego przycisku w trybie ciemnym nie może być takie samo jak aktywnego",
            DarkColorScheme.primary,
            DarkColorScheme.disabledButtonContainer()
        )
    }

    private fun contentOn(scheme: ColorScheme, background: Color) =
        contrast(scheme.disabledButtonContent(), background)

    @Test
    fun `wylaczona tresc bez kontenera na powierzchni w trybie jasnym ma co najmniej 3 do 1`() {
        val ratio = contentOn(LightColorScheme, LightColorScheme.surface)
        assertTrue("wyłączony TextButton/IconButton na surface w trybie jasnym ma kontrast $ratio, wymagane co najmniej 3.0", ratio >= 3.0)
    }

    @Test
    fun `wylaczona tresc bez kontenera na powierzchni w trybie ciemnym ma co najmniej 3 do 1`() {
        val ratio = contentOn(DarkColorScheme, DarkColorScheme.surface)
        assertTrue("wyłączony TextButton/IconButton na surface w trybie ciemnym ma kontrast $ratio, wymagane co najmniej 3.0", ratio >= 3.0)
    }

    @Test
    fun `wylaczona tresc bez kontenera na karcie w trybie jasnym ma co najmniej 3 do 1`() {
        val ratio = contentOn(LightColorScheme, LightColorScheme.surfaceVariant)
        assertTrue("wyłączony IconButton na surfaceVariant (karta) w trybie jasnym ma kontrast $ratio, wymagane co najmniej 3.0", ratio >= 3.0)
    }

    @Test
    fun `wylaczona tresc bez kontenera na karcie w trybie ciemnym ma co najmniej 3 do 1`() {
        val ratio = contentOn(DarkColorScheme, DarkColorScheme.surfaceVariant)
        assertTrue("wyłączony IconButton na surfaceVariant (karta) w trybie ciemnym ma kontrast $ratio, wymagane co najmniej 3.0", ratio >= 3.0)
    }

    @Test
    fun `wylaczona tresc bez kontenera na tle ekranu ma co najmniej 3 do 1 w obu trybach`() {
        listOf("jasny" to LightColorScheme, "ciemny" to DarkColorScheme).forEach { (name, scheme) ->
            val ratio = contentOn(scheme, scheme.background)
            assertTrue("wyłączona kontrolka na background w trybie $name ma kontrast $ratio, wymagane co najmniej 3.0", ratio >= 3.0)
        }
    }
}
