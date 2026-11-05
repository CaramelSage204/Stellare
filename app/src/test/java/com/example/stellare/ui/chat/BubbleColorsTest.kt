package com.example.stellare.ui.chat

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.stellare.data.settings.BubbleColorChoice
import com.example.stellare.ui.theme.DarkColorScheme
import com.example.stellare.ui.theme.LightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleColorsTest {

    private class Variant(val name: String, val scheme: ColorScheme, val dark: Boolean)

    private val variants = listOf(Variant("jasny", LightColorScheme, false), Variant("ciemny", DarkColorScheme, true))

    private fun contrast(foreground: Color, background: Color): Double {
        val a = foreground.luminance().toDouble() + 0.05
        val b = background.luminance().toDouble() + 0.05
        return maxOf(a, b) / minOf(a, b)
    }

    private fun ratioOf(v: Variant, choice: BubbleColorChoice, isOutgoing: Boolean): Double {
        val colors = bubbleColorsFor(v.scheme, v.dark, choice, isOutgoing)
        return contrast(colors.content, colors.container)
    }

    private fun borderRatioOf(v: Variant, choice: BubbleColorChoice, isOutgoing: Boolean): Double =
        contrast(bubbleColorsFor(v.scheme, v.dark, choice, isOutgoing).border, v.scheme.background)

    @Test
    fun `kazda para tlo tekst ma kontrast co najmniej 4,5 w obu motywach`() {
        val failures = mutableListOf<String>()
        BubbleColorChoice.entries.forEach { choice ->
            variants.forEach { v ->
                val themeName = v.name
                listOf(true, false).forEach { outgoing ->
                    val ratio = ratioOf(v, choice, outgoing)
                    if (ratio < MIN_CONTRAST) {
                        val side = if (outgoing) "wychodzący" else "przychodzący"
                        failures += "$choice, motyw $themeName, dymek $side: kontrast $ratio"
                    }
                }
            }
        }
        assertTrue("pary poniżej $MIN_CONTRAST:1 (nieczytelny czat): $failures", failures.isEmpty())
    }

    @Test
    fun `domyslny wybor zachowuje kolory z etapu 6`() {
        variants.forEach { v ->
            val themeName = v.name
            val scheme = v.scheme
            val outgoing = bubbleColorsFor(scheme, v.dark, BubbleColorChoice.PEACH, true)
            assertEquals("tło wychodzącego w motywie $themeName", scheme.primaryContainer, outgoing.container)
            assertEquals("tekst wychodzącego w motywie $themeName", scheme.onPrimaryContainer, outgoing.content)
            val incoming = bubbleColorsFor(scheme, v.dark, BubbleColorChoice.PEACH, false)
            assertEquals("tło przychodzącego w motywie $themeName", scheme.surfaceVariant, incoming.container)
            assertEquals("tekst przychodzącego w motywie $themeName", scheme.onSurfaceVariant, incoming.content)
        }
    }

    @Test
    fun `kazdy wybor daje inne tlo wychodzacego dymka w obu motywach`() {
        variants.forEach { v ->
            val themeName = v.name
            val scheme = v.scheme
            val containers = BubbleColorChoice.entries.map { bubbleColorsFor(scheme, v.dark, it, true).container }
            assertEquals("tła wychodzących w motywie $themeName mają być różne: $containers", containers.size, containers.toSet().size)
        }
    }

    @Test
    fun `wybor nie zmienia dymka przychodzacego`() {
        variants.forEach { v ->
            val themeName = v.name
            val scheme = v.scheme
            val reference = bubbleColorsFor(scheme, v.dark, BubbleColorChoice.PEACH, false)
            BubbleColorChoice.entries.forEach { choice ->
                assertEquals("dymek przychodzący dla $choice w motywie $themeName", reference, bubbleColorsFor(scheme, v.dark, choice, false))
            }
        }
    }

    @Test
    fun `wychodzacy dymek odrozni sie od przychodzacego`() {
        variants.forEach { v ->
            val themeName = v.name
            val scheme = v.scheme
            BubbleColorChoice.entries.forEach { choice ->
                assertNotEquals(
                    "dla $choice w motywie $themeName oba dymki mają to samo tło",
                    bubbleColorsFor(scheme, v.dark, choice, true).container,
                    bubbleColorsFor(scheme, v.dark, choice, false).container
                )
            }
        }
    }

    @Test
    fun `obrys kazdego dymka ma kontrast co najmniej 3 wobec tla czatu w obu motywach`() {
        val failures = mutableListOf<String>()
        BubbleColorChoice.entries.forEach { choice ->
            variants.forEach { v ->
                listOf(true, false).forEach { outgoing ->
                    val ratio = borderRatioOf(v, choice, outgoing)
                    if (ratio < MIN_BORDER_CONTRAST) {
                        val side = if (outgoing) "wychodzący" else "przychodzący"
                        failures += "$choice, motyw ${v.name}, dymek $side: obrys wobec tła $ratio"
                    }
                }
            }
        }
        assertTrue("dymki zlewające się z tłem czatu (poniżej $MIN_BORDER_CONTRAST:1): $failures", failures.isEmpty())
    }

    @Test
    fun `pomiar kontrastu zglasza wartosci`() {
        BubbleColorChoice.entries.forEach { choice ->
            variants.forEach { v ->
                val themeName = v.name
                println("KONTRAST $choice $themeName wychodzacy=${ratioOf(v, choice, true)} przychodzacy=${ratioOf(v, choice, false)} obrys=${borderRatioOf(v, choice, true)}")
            }
        }
    }

    private companion object {
        const val MIN_CONTRAST = 4.5
        const val MIN_BORDER_CONTRAST = 3.0
    }
}
