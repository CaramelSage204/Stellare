package com.example.stellare.session

import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.session.RegistrationRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegistrationRulesTest {

    @Test
    fun `pacjent ma cztery kroki`() {
        assertEquals(4, RegistrationRules.totalSteps(UserRole.PATIENT))
    }

    @Test
    fun `psycholog ma piec krokow`() {
        assertEquals(5, RegistrationRules.totalSteps(UserRole.PSYCHOLOGIST))
    }

    @Test
    fun `student psychologii ma piec krokow`() {
        assertEquals(5, RegistrationRules.totalSteps(UserRole.PSYCHOLOGY_STUDENT))
    }

    @Test
    fun `kazda rola ma okreslona liczbe krokow`() {
        UserRole.values().forEach { assertEquals(
            if (it == UserRole.PATIENT) 4 else 5,
            RegistrationRules.totalSteps(it)
        ) }
    }

    @Test
    fun `pierwszy krok pokazuje ten sam napis niezaleznie od wybranej roli`() {
        val napisy = UserRole.values().map { RegistrationRules.stepLabel(1, it) }.toSet()
        assertEquals(napisy.toString(), 1, napisy.size)
    }

    @Test
    fun `napis kroku od drugiego podaje liczbe krokow roli`() {
        assertEquals("Krok 2 z 4", RegistrationRules.stepLabel(2, UserRole.PATIENT))
        assertEquals("Krok 2 z 5", RegistrationRules.stepLabel(2, UserRole.PSYCHOLOGIST))
        assertEquals("Krok 5 z 5", RegistrationRules.stepLabel(5, UserRole.PSYCHOLOGY_STUDENT))
    }

    @Test
    fun `pasek postepu pierwszego kroku nie zalezy od roli`() {
        val wartosci = UserRole.values().map { RegistrationRules.progress(1, it) }.toSet()
        assertEquals(wartosci.toString(), 1, wartosci.size)
    }

    @Test
    fun `pasek postepu konczy sie pelny w ostatnim kroku kazdej roli`() {
        UserRole.values().forEach {
            assertEquals(1f, RegistrationRules.progress(RegistrationRules.totalSteps(it), it), 0f)
        }
    }

    @Test
    fun `cena dodatnia jest przyjmowana`() {
        assertEquals(150.0, RegistrationRules.parsePrice("150")!!, 0.0)
    }

    @Test
    fun `cena ze spacjami jest przyjmowana`() {
        assertEquals(99.0, RegistrationRules.parsePrice("  99 ")!!, 0.0)
    }

    @Test
    fun `cena ulamkowa jest odrzucana bo monety sa calkowite`() {
        assertNull(RegistrationRules.parsePrice("99,5"))
        assertNull(RegistrationRules.parsePrice("0.5"))
        assertEquals(RegistrationRules.PRICE_INVALID, RegistrationRules.priceError("0,5"))
    }

    @Test
    fun `cena zero jest odrzucana`() {
        assertNull(RegistrationRules.parsePrice("0"))
        assertEquals(RegistrationRules.PRICE_INVALID, RegistrationRules.priceError("0"))
    }

    @Test
    fun `cena ujemna jest odrzucana`() {
        assertNull(RegistrationRules.parsePrice("-10"))
        assertEquals(RegistrationRules.PRICE_INVALID, RegistrationRules.priceError("-10"))
    }

    @Test
    fun `cena nie bedaca liczba jest odrzucana`() {
        assertNull(RegistrationRules.parsePrice("abc"))
        assertNull(RegistrationRules.parsePrice("NaN"))
        assertNull(RegistrationRules.parsePrice("Infinity"))
        assertEquals(RegistrationRules.PRICE_INVALID, RegistrationRules.priceError("abc"))
    }

    @Test
    fun `puste pole ceny daje komunikat o wymaganiu`() {
        assertEquals(RegistrationRules.PRICE_REQUIRED, RegistrationRules.priceError(""))
        assertEquals(RegistrationRules.PRICE_REQUIRED, RegistrationRules.priceError("   "))
    }

    @Test
    fun `poprawna cena nie ma bledu`() {
        assertNull(RegistrationRules.priceError("120"))
    }
}
