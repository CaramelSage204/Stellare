package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.EscrowSettlement
import com.example.stellare.data.model.Settlement
import com.example.stellare.ui.calendar.DepositState
import com.example.stellare.ui.calendar.RefundMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefundMessagesTest {

    private val now = 1_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private val base = AppointmentModel(
        appointmentId = "t1", psychologistId = "p1", patientId = "k1",
        status = "BOOKED", priceCoins = 80, startsAtMillis = now + 3 * day
    )
    private val held = DepositState.Known(EscrowModel("t1", "k1", "p1", 80))

    @Test
    fun `pacjent ponizej 24 h - komunikat mowi ze monety nie wracaja`() {
        val text = RefundMessages.beforeCancel(base.copy(startsAtMillis = now + 2 * 60 * 60 * 1000), held, false, now)
        assertTrue(text.contains("mniej niż 24 godziny"))
        assertTrue(text.contains("monety nie wracają"))
    }

    @Test
    fun `psycholog - komunikat mowi o natychmiastowym zwrocie z depozytu, nie z jego portfela`() {
        val text = RefundMessages.beforeCancel(base, held, true, now)
        assertTrue(text.contains("od razu"))
        assertTrue(text.contains("z depozytu"))
        assertFalse("psycholog nie płaci zwrotu z portfela", text.contains("portfela"))
        assertTrue(text.contains("80"))
    }

    @Test
    fun `pacjent ponad 24 h - komunikat mowi, ze monety wroca na jego konto, a nie ze straci kolejne`() {
        val text = RefundMessages.beforeCancel(base, held, false, now)
        assertTrue("pacjent ma przeczytać, że odzyska 80 monet", text.contains("wróci na Twoje konto"))
        assertTrue(text.contains("80"))
        assertFalse("dialog nie może straszyć pacjenta pobraniem z portfela", text.contains("portfela"))
        assertFalse(text.contains("Pacjent od razu dostanie"))
    }

    @Test
    fun `wizyta bez ceny - komunikat nie obiecuje ani nie odbiera monet`() {
        val text = RefundMessages.beforeCancel(base.copy(priceCoins = 0), DepositState.Known(null), false, now)
        assertFalse(text.contains("monety nie wracają"))
        assertTrue(text.contains("nie wiąże się z żadnymi monetami"))
    }

    @Test
    fun `po odwolaniu przez psychologa komunikat mowi o otrzymanym zwrocie`() {
        assertTrue(RefundMessages.afterCancel(CancelOutcome.Refunded(80), viewerIsPsychologist = true).contains("otrzymał zwrot 80"))
    }

    @Test
    fun `po odwolaniu bez zwrotu komunikat nie wspomina o zwrocie`() {
        assertFalse(RefundMessages.afterCancel(CancelOutcome.CancelledWithoutRefund, viewerIsPsychologist = true).contains("zwrot"))
        assertFalse(RefundMessages.afterCancel(CancelOutcome.CancelledWithoutRefund, viewerIsPsychologist = false).contains("zwrot"))
    }

    @Test
    fun `po odwolaniu przez pacjenta komunikat mowi do pacjenta, ze monety wrocily na jego konto`() {
        val text = RefundMessages.afterCancel(CancelOutcome.Refunded(80), viewerIsPsychologist = false)
        assertTrue(text.contains("80 monet wróciło na Twoje konto"))
        assertFalse("pacjent nie czyta o sobie w trzeciej osobie", text.contains("Pacjent otrzymał"))
    }

    @Test
    fun `wizyta bez dokumentu depozytu - ani pacjent, ani psycholog nie czyta obietnicy zwrotu`() {
        val brak = DepositState.Known(null)
        listOf(false, true).forEach { who ->
            val text = RefundMessages.beforeCancel(base, brak, who, now)
            assertEquals(RefundMessages.NO_DEPOSIT, text)
            assertFalse(text.contains("wróci"))
            assertFalse(text.contains("dostanie zwrot"))
            assertFalse("brak depozytu to nie jest okno 24 h", text.contains("24"))
        }
    }

    @Test
    fun `depozyt juz rozliczony - komunikat nie obiecuje przeniesienia monet`() {
        val rozliczony = DepositState.Known(EscrowModel("t1", "k1", "p1", 80, EscrowModel.REFUNDED))
        listOf(false, true).forEach { who ->
            assertEquals(RefundMessages.SETTLED_DEPOSIT, RefundMessages.beforeCancel(base, rozliczony, who, now))
        }
    }

    @Test
    fun `dopoki depozyt sie wczytuje, komunikat nie twierdzi niczego o monetach`() {
        listOf(false, true).forEach { who ->
            val text = RefundMessages.beforeCancel(base, DepositState.Loading, who, now)
            assertEquals(RefundMessages.CHECKING, text)
            assertFalse(text.contains("wróci"))
            assertFalse(text.contains("nie wracają"))
        }
    }

    @Test
    fun `gdy depozytu nie da sie odczytac, komunikat to mowi zamiast zgadywac`() {
        listOf(false, true).forEach { who ->
            assertEquals(RefundMessages.UNAVAILABLE, RefundMessages.beforeCancel(base, DepositState.Unavailable, who, now))
        }
    }

    @Test
    fun `po odzyskaniu monet komunikat mowi o odzyskaniu, a nie o odwolaniu wizyty`() {
        val text = RefundMessages.afterReclaim(CancelOutcome.Refunded(80))
        assertTrue(text.contains("Odzyskano 80"))
        assertFalse(text.contains("odwołana"))
    }

    @Test
    fun `tekst przed odwolaniem zgadza sie z decyzja EscrowSettlement dla kazdego terminu i kazdej strony`() {
        val halfHour = 30L * 60 * 1000
        var refunds = 0
        var releases = 0
        for (offset in (-2 * day)..(3 * day) step halfHour) {
            for (who in listOf("k1", "p1")) {
                val viewerIsPsychologist = who == "p1"
                val appointment = base.copy(startsAtMillis = now + offset)
                val escrow = EscrowModel("t1", "k1", "p1", 80)
                val decision = EscrowSettlement.decide(escrow, appointment.copy(status = "CANCELLED", cancelledBy = who), who, now)
                val text = RefundMessages.beforeCancel(appointment, DepositState.Known(escrow), viewerIsPsychologist, now)
                when (decision) {
                    is Settlement.RefundToPatient -> {
                        refunds++
                        assertTrue("$who przy offset=$offset: decyzja to zwrot, a tekst: $text", text.contains("${decision.amount} monet") && !text.contains("nie wracają"))
                    }
                    is Settlement.ReleaseToPsychologist -> {
                        releases++
                        assertTrue("$who przy offset=$offset: decyzja to wypłata psychologowi, a tekst: $text", text.contains("monety nie wracają") && text.contains("trafi do psychologa"))
                    }
                    is Settlement.NotSettleable -> assertNotEquals(RefundMessages.CHECKING, text)
                }
            }
        }
        assertTrue("test musi zobaczyć zwroty i wypłaty, zobaczył $refunds / $releases", refunds > 100 && releases > 20)
    }

    @Test
    fun `zakonczenie wizyty - psycholog czyta, ze depozyt trafi na jego konto, gdy depozyt jest`() {
        val text = RefundMessages.beforeComplete(base, held, now)
        assertTrue(text.contains("80 monet z depozytu"))
        assertTrue(text.contains("na Twoje konto"))
    }

    @Test
    fun `zakonczenie wizyty bez depozytu albo z rozliczonym nie obiecuje monet`() {
        assertEquals(RefundMessages.NO_DEPOSIT_ON_COMPLETE, RefundMessages.beforeComplete(base, DepositState.Known(null), now))
        val rozliczony = DepositState.Known(EscrowModel("t1", "k1", "p1", 80, EscrowModel.REFUNDED))
        assertEquals(RefundMessages.SETTLED_ON_COMPLETE, RefundMessages.beforeComplete(base, rozliczony, now))
        assertEquals(RefundMessages.CHECKING, RefundMessages.beforeComplete(base, DepositState.Loading, now))
        assertEquals(RefundMessages.UNAVAILABLE, RefundMessages.beforeComplete(base, DepositState.Unavailable, now))
        assertEquals(RefundMessages.NO_COINS, RefundMessages.beforeComplete(base.copy(priceCoins = 0), DepositState.Known(null), now))
    }

    @Test
    fun `po pozniejszym odwolaniu przez pacjenta komunikat mowi, ze oplata trafila do psychologa`() {
        val text = RefundMessages.afterCancel(CancelOutcome.Released(80), viewerIsPsychologist = false)
        assertTrue(text, text.contains("Opłata 80 monet nie została zwrócona i trafiła do psychologa"))
    }

    @Test
    fun `po odwolaniu bez ruchu monet komunikat to mowi, a nie milczy`() {
        val text = RefundMessages.afterCancel(CancelOutcome.CancelledWithoutRefund, viewerIsPsychologist = false)
        assertTrue(text, text.contains("Żadne monety nie zostały przeniesione"))
    }

    @Test
    fun `po zakonczeniu wizyty komunikat mowi o wyplacie albo o jej braku`() {
        assertTrue(RefundMessages.afterComplete(com.example.stellare.data.model.CompletionOutcome.Released(80)).contains("80 monet z depozytu trafiło na Twoje konto"))
        assertTrue(RefundMessages.afterComplete(com.example.stellare.data.model.CompletionOutcome.NoCoinsMoved).contains("Żadne monety nie zostały przeniesione"))
    }

    @Test
    fun `komunikat o niedostepnym depozycie mowi, jak ponowic, i nie obiecuje odswiezenia`() {
        assertTrue(RefundMessages.UNAVAILABLE.contains("Zamknij to okno i otwórz je ponownie"))
        assertFalse(RefundMessages.UNAVAILABLE.contains("za chwilę"))
    }

    @Test
    fun `ta sama wizyta daje roznym rolom rozne teksty - rola jest typem, nie napisem`() {
        val soon = base.copy(startsAtMillis = now + 2 * 60 * 60 * 1000)
        assertTrue(RefundMessages.beforeCancel(soon, held, viewerIsPsychologist = true, nowMillis = now).contains("Pacjent od razu dostanie zwrot"))
        assertTrue(RefundMessages.beforeCancel(soon, held, viewerIsPsychologist = false, nowMillis = now).contains("trafi do psychologa"))
    }
}
