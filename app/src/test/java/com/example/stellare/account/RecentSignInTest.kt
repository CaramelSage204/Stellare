package com.example.stellare.account

import com.example.stellare.data.model.RecentSignIn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentSignInTest {

    private val now = 10_000_000L

    @Test
    fun `logowanie sprzed chwili jest swieze`() {
        assertTrue("logowanie sprzed 10 s ma byc swieze", RecentSignIn.isFresh(now - 10_000, now))
    }

    @Test
    fun `logowanie sprzed godziny nie jest swieze`() {
        assertFalse("logowanie sprzed godziny nie moze byc swieze", RecentSignIn.isFresh(now - 3_600_000, now))
    }

    @Test
    fun `granica okna jest wlaczona a chwila po niej nie`() {
        assertTrue("dokladnie na granicy okna", RecentSignIn.isFresh(now - RecentSignIn.WINDOW_MILLIS, now))
        assertFalse("milisekunda po granicy", RecentSignIn.isFresh(now - RecentSignIn.WINDOW_MILLIS - 1, now))
    }

    @Test
    fun `nieznany czas logowania nie jest swiezy`() {
        assertFalse("brak czasu logowania nie moze oznaczac swiezego", RecentSignIn.isFresh(null, now))
    }

    @Test
    fun `lekko wyprzedzajacy zegar serwera nie blokuje`() {
        assertTrue("czas logowania w przyszlosci o kilka sekund", RecentSignIn.isFresh(now + 5_000, now))
    }
}
