package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.ui.calendar.DepositState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DepositStateTest {

    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000
    private val held = DepositState.Known(EscrowModel("t1", "k1", "p1", 70))
    private val missed = AppointmentModel(
        appointmentId = "t1", psychologistId = "p1", patientId = "k1",
        status = "BOOKED", priceCoins = 80, startsAtMillis = now - hour
    )

    @Test
    fun `termin minal i depozyt jest - psycholog ma zwrocic kwote z depozytu, nie z ceny wizyty`() {
        assertEquals(70, held.missedVisitRefund(missed, now))
    }

    @Test
    fun `termin jeszcze nie minal - to nie jest wizyta do zwrotu`() {
        assertNull(held.missedVisitRefund(missed.copy(startsAtMillis = now + hour), now))
    }

    @Test
    fun `bez depozytu, z rozliczonym, w trakcie wczytywania i przy bledzie psycholog nie dostaje obietnicy zwrotu`() {
        assertNull(DepositState.Known(null).missedVisitRefund(missed, now))
        assertNull(DepositState.Known(EscrowModel("t1", "k1", "p1", 70, EscrowModel.REFUNDED)).missedVisitRefund(missed, now))
        assertNull(DepositState.Known(EscrowModel("t1", "k1", "p1", 70, EscrowModel.RELEASED)).missedVisitRefund(missed, now))
        assertNull(DepositState.Loading.missedVisitRefund(missed, now))
        assertNull(DepositState.Unavailable.missedVisitRefund(missed, now))
    }

    @Test
    fun `wizyta bez ceny nie jest wizyta do zwrotu nawet gdy jest dokument depozytu`() {
        assertNull(held.missedVisitRefund(missed.copy(priceCoins = 0), now))
    }
}
