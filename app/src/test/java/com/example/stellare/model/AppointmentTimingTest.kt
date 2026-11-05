package com.example.stellare.model

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentSchedule
import com.example.stellare.data.model.AppointmentTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentTimingTest {

    private val teraz = 1_800_000_000_000L

    private fun wizyta(
        id: String,
        startsAt: Long,
        status: String = "BOOKED",
        priceCoins: Int = 80,
        patientId: String? = "k1"
    ) = AppointmentModel(
        appointmentId = id, psychologistId = "p1", patientId = patientId,
        startsAtMillis = startsAt, status = status, priceCoins = priceCoins
    )

    @Test
    fun `wizyta w przyszlosci jest nadchodzaca`() {
        assertTrue(AppointmentTiming.isUpcoming(wizyta("a", teraz + 1), teraz))
    }

    @Test
    fun `wizyta w przeszlosci nie jest nadchodzaca`() {
        assertFalse(AppointmentTiming.isUpcoming(wizyta("a", teraz - 1), teraz))
    }

    @Test
    fun `wizyta zaczynajaca sie dokladnie teraz juz nie jest nadchodzaca`() {
        assertFalse(AppointmentTiming.isUpcoming(wizyta("a", teraz), teraz))
    }

    @Test
    fun `psycholog moze odwolac wizyte przed terminem`() {
        assertTrue(AppointmentTiming.canCancel(wizyta("a", teraz + 60_000), teraz, isPsychologist = true))
    }

    @Test
    fun `psycholog moze zwrocic monety za wizyte po terminie`() {
        assertTrue(AppointmentTiming.canCancel(wizyta("a", teraz - 60_000), teraz, isPsychologist = true))
    }

    @Test
    fun `pacjent moze odwolac wizyte przed terminem`() {
        assertTrue(AppointmentTiming.canCancel(wizyta("a", teraz + 60_000), teraz, isPsychologist = false))
    }

    @Test
    fun `pacjent po terminie nie ma nic do odwolania`() {
        assertFalse(AppointmentTiming.canCancel(wizyta("a", teraz - 60_000), teraz, isPsychologist = false))
    }

    @Test
    fun `ukonczonej ani odwolanej wizyty nie da sie odwolac zadna rola`() {
        listOf(true, false).forEach { psycholog ->
            listOf("COMPLETED", "CANCELLED", "FREE").forEach { status ->
                assertFalse(
                    "$status, psycholog=$psycholog",
                    AppointmentTiming.canCancel(wizyta("a", teraz - 60_000, status = status), teraz, psycholog)
                )
                assertFalse(
                    "$status, psycholog=$psycholog",
                    AppointmentTiming.canCancel(wizyta("a", teraz + 60_000, status = status), teraz, psycholog)
                )
            }
        }
    }

    @Test
    fun `wizyta po terminie z oplacona cena kwalifikuje sie do zwrotu`() {
        assertTrue(AppointmentTiming.isMissedWithPaidCoins(wizyta("a", teraz - 60_000), teraz))
    }

    @Test
    fun `wizyta przed terminem nie jest wizyta ktora sie nie odbyla`() {
        assertFalse(AppointmentTiming.isMissedWithPaidCoins(wizyta("a", teraz + 60_000), teraz))
    }

    @Test
    fun `wizyta po terminie bez ceny albo bez pacjenta nie ma czego zwracac`() {
        assertFalse(AppointmentTiming.isMissedWithPaidCoins(wizyta("a", teraz - 60_000, priceCoins = 0), teraz))
        assertFalse(AppointmentTiming.isMissedWithPaidCoins(wizyta("a", teraz - 60_000, patientId = null), teraz))
    }

    @Test
    fun `najblizsza granica to start najwczesniejszej przyszlej rezerwacji`() {
        val granica = AppointmentTiming.nextBoundary(
            listOf(wizyta("a", teraz - 5), wizyta("b", teraz + 9_000), wizyta("c", teraz + 3_000), wizyta("d", teraz + 1_000, status = "FREE")),
            teraz
        )
        assertEquals(teraz + 3_000, granica)
    }

    @Test
    fun `bez przyszlych rezerwacji nie ma granicy`() {
        assertNull(AppointmentTiming.nextBoundary(listOf(wizyta("a", teraz - 5)), teraz))
    }

    @Test
    fun `data wyswietlana ma postac dd MM rrrr`() {
        assertEquals("20.10.2026", AppointmentSchedule.displayDate("2026-10-20"))
    }

    @Test
    fun `niepoprawna data jest zwracana bez zmian`() {
        assertEquals("brak", AppointmentSchedule.displayDate("brak"))
        assertEquals("", AppointmentSchedule.displayDate(""))
    }
}
