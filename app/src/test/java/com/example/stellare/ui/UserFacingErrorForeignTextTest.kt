package com.example.stellare.ui

import com.example.stellare.data.model.UserMessageException
import org.junit.Assert.assertEquals
import org.junit.Test

class UserFacingErrorForeignTextTest {

    private val fallback = "Nie udało się."

    @Test
    fun `angielski komunikat bez znacznikow technicznych nie trafia na ekran`() {
        val blad = IllegalStateException("Something went wrong while saving the profile")
        assertEquals("obcy angielski tekst ma dac polski fallback", fallback, UserFacingError.message(blad, fallback))
    }

    @Test
    fun `angielski komunikat zwyklego wyjatku nie trafia na ekran`() {
        val blad = RuntimeException("Connection reset by peer")
        assertEquals("tekst RuntimeException ma dac fallback", fallback, UserFacingError.message(blad, fallback))
    }

    @Test
    fun `angielski komunikat zawiniety w inny wyjatek nie trafia na ekran`() {
        val blad = RuntimeException("wrapper", IllegalArgumentException("Invalid argument supplied"))
        assertEquals("zawiniety obcy tekst ma dac fallback", fallback, UserFacingError.message(blad, fallback))
    }

    @Test
    fun `komunikat domenowy bez polskich liter przechodzi gdy wyjatek jest nasz`() {
        val blad = UserMessageException("Wizyta nie jest zarezerwowana.")
        assertEquals("nasz wyjatek ma pokazac swoj tekst", "Wizyta nie jest zarezerwowana.", UserFacingError.message(blad, fallback))
    }

    @Test
    fun `nasz wyjatek zawiniety w inny tez pokazuje swoj tekst`() {
        val blad = RuntimeException("wrapper", UserMessageException("Nie znaleziono wizyty."))
        assertEquals("zawiniety nasz wyjatek ma pokazac swoj tekst", "Nie znaleziono wizyty.", UserFacingError.message(blad, fallback))
    }

    @Test
    fun `nasz wyjatek z technicznym tekstem nadal daje fallback`() {
        val blad = UserMessageException("PERMISSION_DENIED L12:4")
        assertEquals("techniczny tekst nigdy na ekran", fallback, UserFacingError.message(blad, fallback))
    }

    @Test
    fun `polski komunikat zwyklego wyjatku nie przechodzi`() {
        val blad = IllegalArgumentException("Identyfikator ogłoszenia nie może być pusty.")
        assertEquals("komunikat programisty z polskimi literami ma dac fallback", fallback, UserFacingError.message(blad, fallback))
    }

    @Test
    fun `authMessage dla wyjatku Firebase tez nie przepuszcza obcego tekstu`() {
        val blad = IllegalStateException("Unexpected failure while reading credential")
        assertEquals("sciezka logowania ma dac fallback", fallback, UserFacingError.authMessage(blad, fallback))
    }
}
