package com.example.stellare.appointments

import com.example.stellare.data.model.EscrowSettlement
import com.example.stellare.data.remote.EscrowTransactions
import com.example.stellare.fakes.EscrowTestWorld
import com.example.stellare.fakes.EscrowTestWorld.Companion.DAY
import com.example.stellare.fakes.EscrowTestWorld.Companion.HOUR
import com.example.stellare.fakes.EscrowTestWorld.Companion.START
import com.example.stellare.fakes.EscrowTestWorld.Companion.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tytul wpisu ksiegi nie trafia na ekran (etykiete liczy WalletEntryMapper z pola type), wiec
 * nieprawda w tym polu nie jest widoczna i moze przetrwac dowolnie dlugo. Ksiega jest zapisem
 * powodu przeplywu monet, a te dwa powody sa rozne: odwolana wizyta i wizyta, ktorej nikt nie
 * rozliczyl w oknie 14 dni i ktora wciaz ma status BOOKED.
 */
class RefundLedgerTitleTest {

    private val patient = "k1"
    private val psychologist = "p1"

    private suspend fun bookedWorld(): EscrowTestWorld {
        val world = EscrowTestWorld(slots = listOf(slot("t1", psychologist, 80)))
        world.source.bookAppointmentAtomically("t1", patient, psychologist, 80, "")
        return world
    }

    @Test
    fun `zwrot po odwolaniu wizyty nazywa rzecz po imieniu`() = runTest {
        val world = bookedWorld()

        world.source.cancelBooking("t1", psychologist)

        assertEquals("CANCELLED", world.appointment("t1").status)
        assertEquals(
            "wizyta zostala odwolana, wiec taki tytul wpisu jest prawda",
            EscrowTransactions.CANCELLATION_REFUND_TITLE,
            world.ledger("t1_refund")!!.title
        )
    }

    @Test
    fun `odzyskanie depozytu po oknie zwrotu nie zapisuje w ksiedze, ze wizyta byla odwolana`() = runTest {
        val world = bookedWorld()
        world.now = START + 3 * DAY + EscrowSettlement.ABANDONED_BOOKING_MILLIS + HOUR

        world.source.refundAbandonedBooking("t1", patient)

        val entry = world.ledger("t1_refund")!!
        assertEquals(
            "scenariusz traci sens, jesli wizyta jednak zostala odwolana",
            "BOOKED",
            world.appointment("t1").status
        )
        assertEquals(
            "wizyta ma status BOOKED i nikt jej nie odwolal, wiec tytul \"${EscrowTransactions.CANCELLATION_REFUND_TITLE}\" " +
                "zapisuje w ksiedze nieprawde o powodzie przeplywu monet",
            EscrowTransactions.RECLAIM_REFUND_TITLE,
            entry.title
        )
    }
}
