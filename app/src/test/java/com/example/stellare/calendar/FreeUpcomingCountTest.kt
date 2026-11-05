package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class FreeUpcomingCountTest {

    private val now = 1_000_000L

    private fun slot(id: String, status: String, startsAt: Long) =
        AppointmentModel(appointmentId = id, psychologistId = "p1", status = status, startsAtMillis = startsAt)

    @Test
    fun `statystyka wolnych nie liczy terminow, ktore juz minely`() {
        val slots = listOf(
            slot("a", "FREE", now + 1),
            slot("b", "FREE", now + 5_000),
            slot("c", "FREE", now - 1),
            slot("d", "FREE", now - 9_000)
        )

        assertEquals("przeterminowane wolne terminy nie mogą być liczone — pacjent ich nie widzi", 2, AppointmentOrder.freeUpcomingCount(slots, now))
    }

    @Test
    fun `statystyka wolnych nie liczy zarezerwowanych ani zakonczonych`() {
        val slots = listOf(
            slot("a", "FREE", now + 1),
            slot("b", "BOOKED", now + 1),
            slot("c", "COMPLETED", now + 1),
            slot("d", "CANCELLED", now + 1)
        )

        assertEquals("tylko status FREE jest wolny", 1, AppointmentOrder.freeUpcomingCount(slots, now))
    }

    @Test
    fun `liczba wolnych jest zgodna z lista widoczna dla pacjenta`() {
        val slots = listOf(slot("a", "FREE", now + 1), slot("b", "FREE", now - 1), slot("c", "FREE", now + 7))

        assertEquals(
            "licznik i lista muszą używać tego samego warunku przyszłości",
            AppointmentOrder.upcoming(slots, now).size,
            AppointmentOrder.freeUpcomingCount(slots, now)
        )
    }
}
