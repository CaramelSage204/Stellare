package com.example.stellare.ui.calendar

import com.example.stellare.ui.calendar.components.VisibleMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleMonthTest {

    @Test
    fun `klucz zachowuje rok i miesiac`() {
        val key = VisibleMonth.key(2026, 9)
        assertEquals("rok po odczycie z klucza", 2026, VisibleMonth.year(key))
        assertEquals("miesiąc po odczycie z klucza", 9, VisibleMonth.monthZeroBased(key))
    }

    @Test
    fun `nastepny po grudniu to styczen kolejnego roku`() {
        val next = VisibleMonth.key(2026, 11) + 1
        assertEquals("rok po przejściu przez grudzień", 2027, VisibleMonth.year(next))
        assertEquals("miesiąc po przejściu przez grudzień", 0, VisibleMonth.monthZeroBased(next))
    }

    @Test
    fun `poprzedni przed styczniem to grudzien poprzedniego roku`() {
        val previous = VisibleMonth.key(2026, 0) - 1
        assertEquals("rok po cofnięciu przez styczeń", 2025, VisibleMonth.year(previous))
        assertEquals("miesiąc po cofnięciu przez styczeń", 11, VisibleMonth.monthZeroBased(previous))
    }

    @Test
    fun `wybrana data wyznacza widoczny miesiac`() {
        val key = VisibleMonth.initial("2027-03-15", 2026, 9)
        assertEquals("rok wybranej daty", 2027, VisibleMonth.year(key))
        assertEquals("marzec to miesiąc 2 licząc od zera", 2, VisibleMonth.monthZeroBased(key))
    }

    @Test
    fun `brak daty - widoczny jest biezacy miesiac`() {
        val key = VisibleMonth.initial(null, 2026, 9)
        assertEquals("rok bieżący", 2026, VisibleMonth.year(key))
        assertEquals("miesiąc bieżący", 9, VisibleMonth.monthZeroBased(key))
    }

    @Test
    fun `uszkodzona data - widoczny jest biezacy miesiac zamiast awarii`() {
        listOf("", "abc", "2027-13-01", "2027-00-01", "2027-03").forEach { broken ->
            val key = VisibleMonth.initial(broken, 2026, 9)
            assertEquals("rok dla '$broken'", 2026, VisibleMonth.year(key))
            assertEquals("miesiąc dla '$broken'", 9, VisibleMonth.monthZeroBased(key))
        }
    }
}
