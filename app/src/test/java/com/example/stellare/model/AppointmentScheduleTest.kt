package com.example.stellare.model

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.TimeZone

class AppointmentScheduleTest {

    private val warszawa = TimeZone.getTimeZone("Europe/Warsaw")
    private val utc = TimeZone.getTimeZone("UTC")
    private val nowyJork = TimeZone.getTimeZone("America/New_York")

    @Test
    fun `znana data i godzina daje dokladna liczbe milisekund`() {
        // 2026-10-01 10:00 w Warszawie (CEST, UTC+2) to 08:00 UTC
        val oczekiwane = 1_790_841_600_000L
        assertEquals(oczekiwane, AppointmentSchedule.startsAtMillis("2026-10-01", 10, 0, warszawa))
        assertEquals(oczekiwane, AppointmentSchedule.startsAtMillis("2026-10-01", 8, 0, utc))
    }

    @Test
    fun `ta sama liczba daje inna godzine w innej strefie`() {
        val millis = AppointmentSchedule.startsAtMillis("2026-10-01", 10, 0, warszawa)
        assertEquals("10:00", AppointmentSchedule.timeOf(millis, warszawa))
        assertEquals("08:00", AppointmentSchedule.timeOf(millis, utc))
        assertEquals("04:00", AppointmentSchedule.timeOf(millis, nowyJork))
    }

    @Test
    fun `data przechodzi przez granice doby zaleznie od strefy`() {
        val millis = AppointmentSchedule.startsAtMillis("2026-10-01", 1, 30, warszawa)
        assertEquals("2026-10-01", AppointmentSchedule.dateOf(millis, warszawa))
        assertEquals("2026-09-30", AppointmentSchedule.dateOf(millis, utc))
    }

    @Test
    fun `przeliczenie w obie strony zachowuje date i godzine`() {
        val dni = listOf("2026-01-05", "2026-03-29", "2026-10-25", "2027-02-28", "2028-02-29", "2026-12-31")
        for (dzien in dni) {
            for ((h, m) in listOf(0 to 0, 9 to 5, 12 to 30, 23 to 59)) {
                val millis = AppointmentSchedule.startsAtMillis(dzien, h, m, warszawa)
                val oczekiwanaGodzina = "%02d:%02d".format(h, m)
                val godzina = AppointmentSchedule.timeOf(millis, warszawa)
                assertEquals("$dzien $oczekiwanaGodzina", dzien, AppointmentSchedule.dateOf(millis, warszawa))
                assertEquals("$dzien $oczekiwanaGodzina", oczekiwanaGodzina, godzina)
            }
        }
    }

    @Test
    fun `godzina nieistniejaca przez zmiane czasu jest przesuwana, a lancuchy opisuja liczbe`() {
        val millis = AppointmentSchedule.startsAtMillis("2026-03-29", 2, 30, warszawa)
        assertEquals("2026-03-29", AppointmentSchedule.dateOf(millis, warszawa))
        assertEquals("03:30", AppointmentSchedule.timeOf(millis, warszawa))
    }

    @Test
    fun `godziny i minuty sa dopelniane zerem`() {
        val millis = AppointmentSchedule.startsAtMillis("2026-02-03", 7, 5, utc)
        assertEquals("07:05", AppointmentSchedule.timeOf(millis, utc))
        assertEquals("2026-02-03", AppointmentSchedule.dateOf(millis, utc))
    }

    @Test
    fun `nieprawidlowa data jest odrzucana`() {
        assertThrows(IllegalArgumentException::class.java) {
            AppointmentSchedule.startsAtMillis("2026-13-01", 10, 0, utc)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppointmentSchedule.startsAtMillis("jutro", 10, 0, utc)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppointmentSchedule.startsAtMillis("2026-10-01", 24, 0, utc)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppointmentSchedule.startsAtMillis("2026-10-01", 10, 60, utc)
        }
    }

    @Test
    fun `wolny termin wyprowadza date i godzine z liczby`() {
        val millis = AppointmentSchedule.startsAtMillis("2026-10-01", 10, 0, warszawa)
        val termin = AppointmentModel.freeSlot("p1", millis, 80, warszawa)

        assertEquals(millis, termin.startsAtMillis)
        assertEquals("2026-10-01", termin.date)
        assertEquals("10:00", termin.time)
        assertEquals(80, termin.priceCoins)
        assertEquals("FREE", termin.status)
        assertEquals(null, termin.patientId)
    }
}
