package com.example.stellare.model

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.EscrowSettlement
import com.example.stellare.data.model.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscrowSettlementTest {

    private val teraz = 1_800_000_000_000L
    private val godzina = 60L * 60 * 1000
    private val dzien = 24 * godzina

    private val depozyt = EscrowModel("t1", patientId = "k1", psychologistId = "p1", amount = 80)

    private fun wizyta(status: String, startsAt: Long, cancelledBy: String? = null) = AppointmentModel(
        appointmentId = "t1", psychologistId = "p1", patientId = "k1",
        startsAtMillis = startsAt, status = status, priceCoins = 80, cancelledBy = cancelledBy
    )

    private fun decide(escrow: EscrowModel, appointment: AppointmentModel, by: String) =
        EscrowSettlement.decide(escrow, appointment, by, teraz)

    @Test
    fun `odbyta wizyta rozliczana przez psychologa daje wyplate`() {
        assertEquals(
            Settlement.ReleaseToPsychologist(80),
            decide(depozyt, wizyta("COMPLETED", teraz - dzien), "p1")
        )
    }

    @Test
    fun `odbyta wizyta rozliczana przez pacjenta nie da sie rozliczyc`() {
        assertTrue(decide(depozyt, wizyta("COMPLETED", teraz - dzien), "k1") is Settlement.NotSettleable)
    }

    @Test
    fun `odwolana ponad 24 h przed terminem - pacjent dostaje zwrot`() {
        val odwolana = wizyta("CANCELLED", teraz + 25 * godzina, cancelledBy = "k1")
        assertEquals(Settlement.RefundToPatient(80), decide(depozyt, odwolana, "k1"))
    }

    @Test
    fun `odwolana 23 h przed terminem - pacjent rozliczajacy wyplaca psychologowi`() {
        val odwolana = wizyta("CANCELLED", teraz + 23 * godzina, cancelledBy = "k1")
        assertEquals(Settlement.ReleaseToPsychologist(80), decide(depozyt, odwolana, "k1"))
    }

    @Test
    fun `odwolana ponizej 24 h - psycholog rozliczajacy zawsze moze zwrocic`() {
        val odwolana = wizyta("CANCELLED", teraz + 23 * godzina, cancelledBy = "k1")
        assertEquals(Settlement.RefundToPatient(80), decide(depozyt, odwolana, "p1"))
    }

    @Test
    fun `odwolana przez psychologa tuz przed terminem - pacjent dostaje zwrot`() {
        val odwolana = wizyta("CANCELLED", teraz + godzina, cancelledBy = "p1")
        assertEquals(Settlement.RefundToPatient(80), decide(depozyt, odwolana, "k1"))
    }

    @Test
    fun `granica dokladnie 24 h to wyplata a nie zwrot, jak w regule powyzej 86400000`() {
        val odwolana = wizyta("CANCELLED", teraz + dzien, cancelledBy = "k1")
        assertEquals(
            "reguła oddaje zwrot dopiero przy różnicy > 86400000; równe 86400000 to wypłata",
            Settlement.ReleaseToPsychologist(80),
            decide(depozyt, odwolana, "k1")
        )
        val milisekundaWiecej = wizyta("CANCELLED", teraz + dzien + 1, cancelledBy = "k1")
        assertEquals(Settlement.RefundToPatient(80), decide(depozyt, milisekundaWiecej, "k1"))
    }

    @Test
    fun `odwolana bez znanego odwolujacego tuz przed terminem nie da sie rozliczyc przez pacjenta`() {
        val odwolana = wizyta("CANCELLED", teraz + godzina, cancelledBy = null)
        assertTrue(decide(depozyt, odwolana, "k1") is Settlement.NotSettleable)
    }

    @Test
    fun `depozyt juz RELEASED nie da sie rozliczyc`() {
        val odwolana = wizyta("CANCELLED", teraz + 25 * godzina, cancelledBy = "k1")
        assertTrue(decide(depozyt.copy(status = EscrowModel.RELEASED), odwolana, "k1") is Settlement.NotSettleable)
        assertTrue(decide(depozyt.copy(status = EscrowModel.RELEASED), wizyta("COMPLETED", teraz), "p1") is Settlement.NotSettleable)
    }

    @Test
    fun `depozyt juz REFUNDED nie da sie rozliczyc`() {
        val odwolana = wizyta("CANCELLED", teraz + 25 * godzina, cancelledBy = "p1")
        assertTrue(decide(depozyt.copy(status = EscrowModel.REFUNDED), odwolana, "p1") is Settlement.NotSettleable)
    }

    @Test
    fun `depozyt bez kwoty dodatniej nie da sie rozliczyc`() {
        val odwolana = wizyta("CANCELLED", teraz + 25 * godzina, cancelledBy = "p1")
        assertTrue(decide(depozyt.copy(amount = 0), odwolana, "p1") is Settlement.NotSettleable)
        assertTrue(decide(depozyt.copy(amount = -5), odwolana, "p1") is Settlement.NotSettleable)
    }

    @Test
    fun `osoba trzecia nie rozlicza depozytu`() {
        val odwolana = wizyta("CANCELLED", teraz + 25 * godzina, cancelledBy = "p1")
        assertTrue(decide(depozyt, odwolana, "obcy") is Settlement.NotSettleable)
    }

    @Test
    fun `wizyta zarezerwowana przez 14 dni po terminie daje pacjentowi zwrot`() {
        val przepadla = wizyta("BOOKED", teraz - 14 * dzien - 1)
        assertEquals(Settlement.RefundToPatient(80), decide(depozyt, przepadla, "k1"))
    }

    @Test
    fun `granica dokladnie 14 dni po terminie jeszcze nie daje zwrotu`() {
        assertTrue(decide(depozyt, wizyta("BOOKED", teraz - 14 * dzien), "k1") is Settlement.NotSettleable)
    }

    @Test
    fun `wizyta zarezerwowana nie daje zwrotu przed uplywem okna ani psychologowi`() {
        assertTrue(decide(depozyt, wizyta("BOOKED", teraz - 13 * dzien), "k1") is Settlement.NotSettleable)
        assertTrue(decide(depozyt, wizyta("BOOKED", teraz - 30 * dzien), "p1") is Settlement.NotSettleable)
    }
}
