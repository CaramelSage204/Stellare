package com.example.stellare.session

import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.session.RegistrationRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegistrationRulesStepBlockTest {

    private fun block(
        step: Int,
        role: UserRole = UserRole.PATIENT,
        firstName: String = "Jan",
        lastName: String = "Kowalski",
        ageText: String = "30",
        phone: String = "",
        priceText: String = ""
    ) = RegistrationRules.stepBlockMessage(step, role, firstName, lastName, ageText, phone, priceText)

    @Test
    fun `krok danych osobowych blokuje puste imie`() {
        assertEquals("puste imie ma blokowac przejscie dalej", RegistrationRules.FIRST_NAME_REQUIRED, block(2, firstName = " "))
    }

    @Test
    fun `krok danych osobowych blokuje puste nazwisko`() {
        assertEquals("puste nazwisko ma blokowac przejscie dalej", RegistrationRules.LAST_NAME_REQUIRED, block(2, lastName = ""))
    }

    @Test
    fun `krok danych osobowych blokuje pusty wiek zamiast zapisac zero`() {
        assertEquals("pusty wiek ma blokowac przejscie dalej", RegistrationRules.AGE_INVALID, block(2, ageText = ""))
        assertEquals("wiek zero ma blokowac przejscie dalej", RegistrationRules.AGE_INVALID, block(2, ageText = "0"))
    }

    @Test
    fun `krok danych osobowych przepuszcza komplet`() {
        assertNull(block(2))
    }

    @Test
    fun `krok telefonu przepuszcza pusty numer`() {
        assertNull("numer jest opcjonalny", block(3, phone = ""))
        assertNull("numer z samych spacji to brak numeru", block(3, phone = "   "))
    }

    @Test
    fun `krok telefonu przepuszcza poprawny numer bez zadnego kodu`() {
        assertNull("poprawny numer nie wymaga kodu w rejestracji", block(3, phone = "+48 501 234 567"))
    }

    @Test
    fun `krok telefonu blokuje numer, ktorego nie da sie potwierdzic`() {
        assertEquals("niepoprawny numer ma blokowac przejscie dalej", RegistrationRules.PHONE_INVALID, block(3, phone = "12345"))
    }

    @Test
    fun `krok ceny blokuje tylko zawodowe role`() {
        assertEquals(RegistrationRules.PRICE_REQUIRED, block(5, role = UserRole.PSYCHOLOGIST, priceText = ""))
        assertEquals(RegistrationRules.PRICE_INVALID, block(5, role = UserRole.PSYCHOLOGY_STUDENT, priceText = "abc"))
        assertNull(block(5, role = UserRole.PSYCHOLOGIST, priceText = "120"))
    }

    @Test
    fun `krok danych logowania nie ma blokady`() {
        assertNull(block(4, firstName = "", ageText = ""))
    }
}
