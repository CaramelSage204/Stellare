package com.example.stellare.model

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.Settlement
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.RefundPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefundPolicyTest {

    private val teraz = 1_800_000_000_000L
    private val godzina = 60L * 60 * 1000

    private val zarezerwowana = AppointmentModel(
        appointmentId = "t1", psychologistId = "p1", patientId = "k1",
        startsAtMillis = teraz + 3 * 24 * godzina,
        status = "BOOKED", priceCoins = 80
    )

    private fun depozyt(kwota: Int = 80, status: String = EscrowModel.HELD) =
        EscrowModel("t1", "k1", "p1", kwota, status)

    @Test
    fun `psycholog odwolujacy zarezerwowana wizyte zwraca pelna cene`() {
        assertEquals(Settlement.RefundToPatient(80), RefundPolicy.settlement(zarezerwowana, depozyt(), "p1", teraz))
    }

    @Test
    fun `psycholog zwraca takze tuz przed terminem`() {
        val bliska = zarezerwowana.copy(startsAtMillis = teraz + 2 * godzina)
        assertEquals(Settlement.RefundToPatient(80), RefundPolicy.settlement(bliska, depozyt(), "p1", teraz))
    }

    @Test
    fun `pacjent odwolujacy ponad 24 h przed terminem dostaje zwrot z depozytu od razu`() {
        assertEquals(Settlement.RefundToPatient(80), RefundPolicy.settlement(zarezerwowana, depozyt(), "k1", teraz))
    }

    @Test
    fun `pacjent odwolujacy ponizej 24 h nie dostaje zwrotu`() {
        val bliska = zarezerwowana.copy(startsAtMillis = teraz + 2 * godzina)
        assertEquals(Settlement.ReleaseToPsychologist(80), RefundPolicy.settlement(bliska, depozyt(), "k1", teraz))
    }

    @Test
    fun `pacjent dokladnie 24 h przed terminem nie dostaje zwrotu`() {
        val granica = zarezerwowana.copy(startsAtMillis = teraz + 24 * godzina)
        assertEquals(Settlement.ReleaseToPsychologist(80), RefundPolicy.settlement(granica, depozyt(), "k1", teraz))
    }

    @Test
    fun `pacjent odwolujacy termin bez startsAtMillis nie dostaje zwrotu`() {
        val bezTerminu = zarezerwowana.copy(startsAtMillis = 0L)
        assertEquals(Settlement.ReleaseToPsychologist(80), RefundPolicy.settlement(bezTerminu, depozyt(), "k1", teraz))
    }

    @Test
    fun `wizyta niezarezerwowana nie daje zwrotu`() {
        val wolna = zarezerwowana.copy(status = "FREE")
        assertNull(RefundPolicy.settlement(wolna, depozyt(), "p1", teraz))
    }

    @Test
    fun `wizyta bez pacjenta nie daje zwrotu`() {
        val bezPacjenta = zarezerwowana.copy(patientId = null)
        assertNull(RefundPolicy.settlement(bezPacjenta, depozyt(), "p1", teraz))
    }

    @Test
    fun `wizyta darmowa nie daje zwrotu`() {
        val darmowa = zarezerwowana.copy(priceCoins = 0)
        assertNull(RefundPolicy.settlement(darmowa, depozyt(), "p1", teraz))
        assertNull(RefundPolicy.settlement(darmowa, depozyt(), "k1", teraz))
    }

    @Test
    fun `osoba trzecia nie uruchamia zwrotu`() {
        assertEquals(Settlement.NotSettleable("Tylko strona wizyty może rozliczyć depozyt."), RefundPolicy.settlement(zarezerwowana, depozyt(), "obcy", teraz))
    }

    @Test
    fun `kwota zwrotu rowna sie cenie wizyty`() {
        val droga = zarezerwowana.copy(priceCoins = 125)
        assertEquals(Settlement.RefundToPatient(125), RefundPolicy.settlement(droga, depozyt(125), "p1", teraz))
        assertEquals(Settlement.RefundToPatient(125), RefundPolicy.settlement(droga, depozyt(125), "k1", teraz))
    }

    @Test
    fun `wizyta bez dokumentu depozytu nie daje zwrotu nikomu - ani pacjentowi, ani przy odwolaniu przez psychologa`() {
        assertNull(RefundPolicy.settlement(zarezerwowana, null, "p1", teraz))
        assertNull(RefundPolicy.settlement(zarezerwowana, null, "k1", teraz))
    }

    @Test
    fun `depozyt juz rozliczony nie daje zwrotu`() {
        assertEquals(
            Settlement.NotSettleable("Depozyt został już rozliczony."),
            RefundPolicy.settlement(zarezerwowana, depozyt(status = EscrowModel.REFUNDED), "p1", teraz)
        )
    }

    @Test
    fun `zwrot dotyczy kwoty depozytu, nie ceny wizyty`() {
        assertEquals(Settlement.RefundToPatient(60), RefundPolicy.settlement(zarezerwowana, depozyt(60), "p1", teraz))
    }
}
