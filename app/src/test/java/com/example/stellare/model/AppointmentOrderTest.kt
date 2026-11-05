package com.example.stellare.model

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class AppointmentOrderTest {

    private fun slot(id: String, startsAt: Long, date: String = "") =
        AppointmentModel(appointmentId = id, startsAtMillis = startsAt, date = date)

    @Test
    fun `terminy sa w kolejnosci chronologicznej`() {
        val wejscie = listOf(slot("15", 1_500), slot("08", 800), slot("11", 1_100))
        assertEquals(listOf("08", "11", "15"), AppointmentOrder.chronological(wejscie).map { it.appointmentId })
    }

    @Test
    fun `kolejnosc zalezy od startsAtMillis a nie od tekstowej daty`() {
        val wejscie = listOf(
            slot("pozniej", 2_000, date = "2026-09-30"),
            slot("wczesniej", 1_000, date = "2026-10-01")
        )
        assertEquals(listOf("wczesniej", "pozniej"), AppointmentOrder.chronological(wejscie).map { it.appointmentId })
    }

    @Test
    fun `terminy o tym samym czasie zachowuja kolejnosc wejsciowa`() {
        val wejscie = listOf(slot("a", 1_000), slot("b", 1_000), slot("c", 500))
        assertEquals(listOf("c", "a", "b"), AppointmentOrder.chronological(wejscie).map { it.appointmentId })
    }

    @Test
    fun `pusta lista pozostaje pusta`() {
        assertEquals(emptyList<AppointmentModel>(), AppointmentOrder.chronological(emptyList()))
    }
}
