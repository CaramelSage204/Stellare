package com.example.stellare.model

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentOrderDirectionTest {

    private val teraz = 10_000L

    private fun slot(id: String, startsAt: Long) = AppointmentModel(appointmentId = id, startsAtMillis = startsAt)

    private fun ids(list: List<AppointmentModel>) = list.map { it.appointmentId }

    @Test
    fun `terminy przyszle ida rosnaco czyli najblizszy pierwszy`() {
        val wynik = AppointmentOrder.upcoming(listOf(slot("daleki", 30_000), slot("bliski", 11_000), slot("srodek", 20_000)), teraz)
        assertEquals("przyszle maja isc rosnaco, najblizszy pierwszy", listOf("bliski", "srodek", "daleki"), ids(wynik))
    }

    @Test
    fun `wizyta bez czasu startu nie trafia na poczatek listy przyszlych`() {
        val wynik = AppointmentOrder.upcoming(listOf(slot("bez-czasu", 0), slot("bliski", 11_000)), teraz)
        assertEquals("zerowy start nie moze byc na liscie przyszlych", listOf("bliski"), ids(wynik))
    }

    @Test
    fun `wizyta bez czasu startu nie jest przyszla nawet gdy zegar pokazuje zero`() {
        assertTrue("zerowy start przy zerowym zegarze nie jest przyszly", AppointmentOrder.upcoming(listOf(slot("z", 0)), 0L).isEmpty())
    }

    @Test
    fun `termin o starcie rownym teraz jest przeszly`() {
        assertTrue("start rowny teraz nie jest przyszly", AppointmentOrder.upcoming(listOf(slot("rowny", teraz)), teraz).isEmpty())
    }
}
