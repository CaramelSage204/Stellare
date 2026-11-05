package com.example.stellare.psychologistdetail

import com.example.stellare.ui.psychologistdetail.parseSpecializations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecializationListTest {

    @Test
    fun `wiele wartosci rozdzielonych przecinkami`() {
        assertEquals(listOf("Rodzina", "Praca"), parseSpecializations("Rodzina, Praca"))
    }

    @Test
    fun `jedna wartosc`() {
        assertEquals(listOf("Psychoterapeuta"), parseSpecializations("Psychoterapeuta"))
    }

    @Test
    fun `puste pole daje pusta liste`() {
        assertTrue(parseSpecializations("").isEmpty())
    }

    @Test
    fun `same spacje i przecinki daja pusta liste`() {
        assertTrue(parseSpecializations("  ,  , ,").isEmpty())
    }

    @Test
    fun `nadmiarowe spacje sa obcinane`() {
        assertEquals(listOf("Rodzina", "Praca"), parseSpecializations("   Rodzina   ,    Praca   "))
    }

    @Test
    fun `puste elementy miedzy przecinkami sa pomijane`() {
        assertEquals(listOf("Rodzina", "Praca"), parseSpecializations(",Rodzina,,, Praca,"))
    }

    @Test
    fun `powtorzenia sa scalane z zachowaniem kolejnosci`() {
        assertEquals(listOf("Praca", "Rodzina"), parseSpecializations("Praca, Rodzina, Praca"))
    }

    @Test
    fun `wartosci spoza listy kategorii nie sa dopisywane ani gubione`() {
        assertEquals(listOf("Rodzina", "Ogólny"), parseSpecializations("Rodzina, Ogólny"))
    }
}
