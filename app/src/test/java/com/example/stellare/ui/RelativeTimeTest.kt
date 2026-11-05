package com.example.stellare.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.TimeZone

class RelativeTimeTest {

    private val now = 1_800_000_000_000L
    private val minuta = 60_000L
    private val godzina = 60 * minuta
    private val dzien = 24 * godzina
    private val utc = TimeZone.getTimeZone("UTC")

    private fun przed(ms: Long) = RelativeTime.format(now - ms, now, utc)

    @Test
    fun `mniej niz minuta to przed chwila`() {
        assertEquals("przed chwilą", przed(0))
        assertEquals("przed chwilą", przed(59_000))
    }

    @Test
    fun `czas z przyszlosci to przed chwila`() = assertEquals("przed chwilą", przed(-5 * minuta))

    @Test
    fun `minuty z poprawna odmiana`() {
        assertEquals("1 minutę temu", przed(minuta))
        assertEquals("2 minuty temu", przed(2 * minuta))
        assertEquals("5 minut temu", przed(5 * minuta))
        assertEquals("12 minut temu", przed(12 * minuta))
        assertEquals("22 minuty temu", przed(22 * minuta))
    }

    @Test
    fun `godziny z poprawna odmiana`() {
        assertEquals("1 godzinę temu", przed(godzina))
        assertEquals("3 godziny temu", przed(3 * godzina))
        assertEquals("5 godzin temu", przed(5 * godzina))
    }

    @Test
    fun `wczoraj i dni`() {
        assertEquals("wczoraj", przed(dzien))
        assertEquals("3 dni temu", przed(3 * dzien))
    }

    @Test
    fun `starsze niz tydzien to data`() {
        val tekst = RelativeTime.format(1_700_000_000_000L, now, utc)
        assertEquals("14.11.2023", tekst)
    }

    @Test
    fun `zerowy czas to brak daty a nie rok 1970`() {
        val tekst = RelativeTime.format(0L, now, utc)
        assertEquals("Brak daty", tekst)
        assertFalse(tekst.contains("1970"))
    }

    @Test
    fun `wynik nie zawiera angielskich slow`() {
        listOf(0L, minuta, 5 * minuta, godzina, dzien, 3 * dzien, 30 * dzien).forEach {
            val tekst = przed(it)
            assertFalse(tekst, tekst.contains("ago") || tekst.contains("minute") || tekst.contains("hour"))
        }
    }
}
