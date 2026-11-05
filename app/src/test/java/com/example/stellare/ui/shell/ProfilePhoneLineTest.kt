package com.example.stellare.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfilePhoneLineTest {

    @Test
    fun `zapisany numer jest pokazany w profilu`() {
        assertEquals("Tel. +48501234567", profilePhoneLine("+48501234567"))
    }

    @Test
    fun `numer ze spacjami wokol jest przyciety`() {
        assertEquals("Tel. +48501234567", profilePhoneLine("  +48501234567 "))
    }

    @Test
    fun `brak numeru oznacza brak wiersza a nie napis o niepotwierdzeniu`() {
        assertNull("pusty numer nie ma wiersza", profilePhoneLine(""))
        assertNull("numer z samych spacji nie ma wiersza", profilePhoneLine("   "))
        assertNull("brak pola nie ma wiersza", profilePhoneLine(null))
    }
}
