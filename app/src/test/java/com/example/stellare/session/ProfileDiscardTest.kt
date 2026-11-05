package com.example.stellare.session

import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.session.ProfileDiscard
import com.example.stellare.ui.session.ProfileDraft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDiscardTest {

    private val initial = ProfileDraft.initial("Anna" to "Nowak", "Kobieta")

    @Test
    fun `nietkniety formularz nie wymaga potwierdzenia bo nie ma czego tracic`() {
        assertFalse(
            "formularz z samymi podpowiedziami z konta Google nie zawiera danych wpisanych przez uzytkownika, wstecz ma po prostu wyjsc",
            ProfileDiscard.needsConfirmation(initial, initial.copy())
        )
    }

    @Test
    fun `zmiana roli wymaga potwierdzenia`() {
        assertTrue(
            "wybrana rola jest wpisanymi danymi, wstecz nie moze jej gubic bez pytania",
            ProfileDiscard.needsConfirmation(initial, initial.copy(role = UserRole.PSYCHOLOGIST))
        )
    }

    @Test
    fun `zmiana imienia wymaga potwierdzenia`() {
        assertTrue(
            "poprawione imie jest wpisanymi danymi",
            ProfileDiscard.needsConfirmation(initial, initial.copy(firstName = "Ania"))
        )
    }

    @Test
    fun `zmiana nazwiska wymaga potwierdzenia`() {
        assertTrue(
            "poprawione nazwisko jest wpisanymi danymi",
            ProfileDiscard.needsConfirmation(initial, initial.copy(lastName = "Kowalska"))
        )
    }

    @Test
    fun `wpisany wiek wymaga potwierdzenia`() {
        assertTrue(
            "wpisany wiek jest wpisanymi danymi",
            ProfileDiscard.needsConfirmation(initial, initial.copy(ageText = "30"))
        )
    }

    @Test
    fun `zmiana plci wymaga potwierdzenia`() {
        assertTrue(
            "wybrana plec jest wpisanymi danymi",
            ProfileDiscard.needsConfirmation(initial, initial.copy(gender = "Mezczyzna"))
        )
    }

    @Test
    fun `wpisany biogram wymaga potwierdzenia`() {
        assertTrue(
            "wpisany biogram jest wpisanymi danymi",
            ProfileDiscard.needsConfirmation(initial, initial.copy(bio = "Pracuje z lekiem"))
        )
    }

    @Test
    fun `wpisana cena wymaga potwierdzenia`() {
        assertTrue(
            "wpisana cena sesji jest wpisanymi danymi",
            ProfileDiscard.needsConfirmation(initial, initial.copy(priceText = "150"))
        )
    }

    @Test
    fun `wpisanie i wymazanie z powrotem nie wymaga potwierdzenia`() {
        val wpisane = initial.copy(ageText = "30")
        val wymazane = wpisane.copy(ageText = "")
        assertFalse(
            "po wymazaniu wpisanego tekstu formularz jest znowu taki jak na starcie, nie ma czego tracic",
            ProfileDiscard.needsConfirmation(initial, wymazane)
        )
    }
}
