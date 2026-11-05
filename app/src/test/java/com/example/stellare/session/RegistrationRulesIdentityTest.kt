package com.example.stellare.session

import com.example.stellare.ui.session.RegistrationRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegistrationRulesIdentityTest {

    @Test
    fun `komplet danych osobowych przechodzi`() {
        assertNull(RegistrationRules.identityError("Jan", "Kowalski", "30"))
    }

    @Test
    fun `puste imie lub nazwisko daje komunikat`() {
        assertEquals(RegistrationRules.FIRST_NAME_REQUIRED, RegistrationRules.identityError(" ", "Kowalski", "30"))
        assertEquals(RegistrationRules.LAST_NAME_REQUIRED, RegistrationRules.identityError("Jan", "", "30"))
    }

    @Test
    fun `wiek musi byc dodatnia liczba calkowita`() {
        listOf("", "x", "0", "-1", "1.5").forEach {
            assertEquals("wiek '$it'", RegistrationRules.AGE_INVALID, RegistrationRules.identityError("Jan", "Kowalski", it))
        }
        assertEquals(41, RegistrationRules.parseAge(" 41 "))
    }
}
