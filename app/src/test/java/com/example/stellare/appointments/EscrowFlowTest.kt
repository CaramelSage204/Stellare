package com.example.stellare.appointments

import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.UserMessageException
import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.data.remote.BalanceDelta
import com.example.stellare.data.remote.FirestoreCollections
import com.example.stellare.data.remote.SettlementWrite
import com.example.stellare.fakes.EscrowTestWorld
import com.example.stellare.fakes.EscrowTestWorld.Companion.DAY
import com.example.stellare.fakes.EscrowTestWorld.Companion.HOUR
import com.example.stellare.fakes.EscrowTestWorld.Companion.START
import com.example.stellare.fakes.EscrowTestWorld.Companion.contactWrite
import com.example.stellare.fakes.EscrowTestWorld.Companion.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EscrowFlowTest {

    private fun world(vararg slots: com.example.stellare.data.model.AppointmentModel, patient: Int = 200, psychologist: Int = 50) =
        EscrowTestWorld(
            patients = mapOf("k1" to patient, "k2" to 200),
            psychologists = mapOf("p1" to psychologist, "p2" to 50),
            slots = slots.toList()
        )

    private suspend fun EscrowTestWorld.book(id: String = "t1", patient: String = "k1") {
        val slot = appointment(id)
        source.bookAppointmentAtomically(id, patient, slot.psychologistId, slot.priceCoins, "")
    }

    private suspend fun failure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (e: IllegalStateException) {
            return e
        }
        fail("operacja miała zostać odrzucona")
        throw AssertionError()
    }

    private fun userUpdates(world: EscrowTestWorld, userId: String) = world.source.transactions.committedBatches
        .flatten()
        .filterIsInstance<SettlementWrite.Update>()
        .filter { it.collection == FirestoreCollections.privateOf(userId) && it.id == FirestoreCollections.CONTACT }

    @Test
    fun `rezerwacja przenosi monety pacjenta do depozytu, a nie do psychologa`() = runTest {
        val w = world(slot("t1", "p1", 80))
        val before = w.coinsInSystem()

        w.book()

        assertEquals(120, w.balance("k1"))
        assertEquals("psycholog zarabia dopiero, gdy wizyta się odbędzie", 50, w.balance("p1"))
        assertEquals(EscrowModel("t1", "k1", "p1", 80, EscrowModel.HELD), w.escrow("t1"))
        assertEquals("BOOKED", w.appointment("t1").status)
        assertEquals("suma sald i depozytów HELD nie może się zmienić przy rezerwacji", before, w.coinsInSystem())
    }

    @Test
    fun `rezerwacja zapisuje wpis BOOKING_PAYMENT o deterministycznym identyfikatorze i nie tworzy wpisu przychodu`() = runTest {
        val w = world(slot("t1", "p1", 80))

        w.book()

        val payment = w.ledger("t1_k1")
        assertNotNull("wpis płatności musi mieć id <wizyta>_<pacjent>, by uzgadnianie po błędzie było możliwe", payment)
        assertEquals(WalletTransactionType.BOOKING_PAYMENT, payment!!.type)
        assertEquals(-80, payment.amount)
        assertEquals("k1", payment.userId)
        assertEquals(1, w.wallet.transactions.value.size)
        assertNull(w.ledger("t1_p1"))
    }

    @Test
    fun `ponowna rezerwacja tego samego terminu przez tego samego pacjenta niczego nie dubluje`() = runTest {
        val w = world(slot("t1", "p1", 80))

        w.book()
        w.book()

        assertEquals(120, w.balance("k1"))
        assertEquals(1, w.wallet.transactions.value.size)
        assertEquals(1, w.source.transactions.commits)
        assertEquals(500, w.coinsInSystem())
    }

    @Test
    fun `rezerwacja bez pokrycia nie zmienia niczego`() = runTest {
        val w = world(slot("t1", "p1", 80), patient = 79)

        failure { w.book() }

        assertEquals("FREE", w.appointment("t1").status)
        assertEquals(79, w.balance("k1"))
        assertNull(w.escrow("t1"))
        assertTrue(w.wallet.transactions.value.isEmpty())
    }

    @Test
    fun `rezerwacja niesie deklaracje rozliczenia w dokumencie pacjenta i nie dotyka dokumentu psychologa`() = runTest {
        val w = world(slot("t1", "p1", 80))

        w.book()

        val patientWrite = userUpdates(w, "k1").single()
        assertEquals(
            "bez deklaracji escrowSettlement w tej samej partii płatna rezerwacja dostanie PERMISSION_DENIED",
            "t1",
            patientWrite.fields["escrowSettlement"]
        )
        assertEquals(BalanceDelta(-80), patientWrite.fields["coinsBalance"])
        assertTrue(userUpdates(w, "p1").isEmpty())
    }

    @Test
    fun `partia rezerwacji bez deklaracji rozliczenia jest odrzucana przez atrape tak jak przez regule`() = runTest {
        val w = world(slot("t1", "p1", 80))
        val withoutDeclaration = listOf(
            SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, "t1", mapOf("patientId" to "k1", "status" to "BOOKED", "notes" to "")),
            SettlementWrite.Create(FirestoreCollections.ESCROWS, "t1", EscrowModel("t1", "k1", "p1", 80)),
            contactWrite("k1", mapOf("coinsBalance" to 120))
        )

        val error = failure { w.attack("k1", withoutDeclaration) }

        assertTrue(error.message!!, error.message!!.contains("deklaracji rozliczenia"))
        assertEquals("FREE", w.appointment("t1").status)
        assertEquals(200, w.balance("k1"))
    }

    @Test
    fun `termin darmowy nie zaklada depozytu i nie rusza ksiegi`() = runTest {
        val w = world(slot("t1", "p1", 0))

        w.book()

        assertEquals("BOOKED", w.appointment("t1").status)
        assertNull(w.escrow("t1"))
        assertEquals(200, w.balance("k1"))
        assertTrue(w.wallet.transactions.value.isEmpty())
    }

    @Test
    fun `saldo zmienione miedzy odczytem a zatwierdzeniem nie psuje rezerwacji, transakcja czyta ponownie`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.source.transactions.beforeFirstCommit = { w.topUp("k1", 50) }

        w.book()

        assertEquals("saldo ma uwzględniać doładowanie, które wpadło w trakcie", 170, w.balance("k1"))
        assertEquals(2, w.source.transactions.attempts)
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
    }

    @Test
    fun `zakonczenie wizyty wyplaca psychologowi depozyt z deklaracja i wpisem przychodu`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        val before = w.coinsInSystem()
        w.now = START + 3 * DAY + HOUR

        w.source.completeAppointment("t1")

        assertEquals("COMPLETED", w.appointment("t1").status)
        assertEquals(EscrowModel.RELEASED, w.escrow("t1")!!.status)
        assertEquals(130, w.balance("p1"))
        assertEquals(120, w.balance("k1"))
        assertEquals(before, w.coinsInSystem())
        val earning = w.ledger("t1_p1")!!
        assertEquals(WalletTransactionType.BOOKING_EARNING, earning.type)
        assertEquals(80, earning.amount)
        assertEquals("p1", earning.userId)
        assertEquals("t1", userUpdates(w, "p1").single().fields["escrowSettlement"])
        assertNotNull(w.source.completions.value["p1_k1"])
    }

    @Test
    fun `zakonczenie wizyty bez depozytu nie rusza monet`() = runTest {
        val w = world(slot("t1", "p1", 0))
        w.book()
        w.now = START + 3 * DAY + HOUR

        w.source.completeAppointment("t1")

        assertEquals("COMPLETED", w.appointment("t1").status)
        assertEquals(50, w.balance("p1"))
        assertTrue(w.wallet.transactions.value.isEmpty())
    }

    @Test
    fun `zakonczenie przed terminem jest odrzucone i depozyt zostaje`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()

        failure { w.source.completeAppointment("t1") }

        assertEquals("BOOKED", w.appointment("t1").status)
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
        assertEquals(50, w.balance("p1"))
    }

    @Test
    fun `drugie zakonczenie tej samej wizyty nie placi drugi raz`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.now = START + 3 * DAY + HOUR

        w.source.completeAppointment("t1")
        w.source.completeAppointment("t1")

        assertEquals(130, w.balance("p1"))
        assertEquals(2, w.wallet.transactions.value.size)
    }

    @Test
    fun `doladowanie psychologa w trakcie zakonczenia i wyplata daja sume obu, a wyplata nie wymaga ponowienia`() = runTest {
        suspend fun attemptsOfCompletion(concurrentTopUp: Boolean): Pair<Int, EscrowTestWorld> {
            val w = world(slot("t1", "p1", 80))
            w.book()
            w.now = START + 3 * DAY + HOUR
            if (concurrentTopUp) w.source.transactions.beforeFirstCommit = { w.topUp("p1", 25) }
            val before = w.source.transactions.attempts
            w.source.completeAppointment("t1")
            return w.source.transactions.attempts - before to w
        }
        val (quietAttempts, _) = attemptsOfCompletion(concurrentTopUp = false)
        val (attempts, w) = attemptsOfCompletion(concurrentTopUp = true)

        assertEquals("przyrost dokłada się do salda zmienionego w trakcie: 50 + 25 + 80, a nie ostatnia zapisana wartość", 155, w.balance("p1"))
        assertEquals(EscrowModel.RELEASED, w.escrow("t1")!!.status)
        assertEquals(
            "równoczesna zmiana salda kontrahenta nie wymusza ponowienia, bo rozliczenie nie czyta jego salda",
            quietAttempts,
            attempts
        )
        assertEquals(500 + 25, w.coinsInSystem())
    }

    @Test
    fun `psycholog odwoluje tuz przed terminem - pacjent dostaje zwrot, psycholog nic nie traci`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.now = START + 3 * DAY - HOUR
        val before = w.coinsInSystem()

        val outcome = w.source.cancelBooking("t1", "p1")

        assertEquals(CancelOutcome.Refunded(80), outcome)
        assertEquals(EscrowModel.REFUNDED, w.escrow("t1")!!.status)
        assertEquals(200, w.balance("k1"))
        assertEquals(50, w.balance("p1"))
        assertEquals("CANCELLED", w.appointment("t1").status)
        assertEquals("p1", w.appointment("t1").cancelledBy)
        assertEquals(before, w.coinsInSystem())
        assertEquals("t1", userUpdates(w, "k1").last().fields["escrowSettlement"])
    }

    @Test
    fun `pacjent odwoluje ponad 24 h przed terminem - zwrot sam sobie`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()

        val outcome = w.source.cancelBooking("t1", "k1")

        assertEquals(CancelOutcome.Refunded(80), outcome)
        assertEquals(EscrowModel.REFUNDED, w.escrow("t1")!!.status)
        assertEquals(200, w.balance("k1"))
        assertEquals("k1", w.appointment("t1").cancelledBy)
    }

    @Test
    fun `pacjent odwoluje krocej niz 24 h przed terminem - w tej samej transakcji depozyt idzie do psychologa`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.now = START + 3 * DAY - 23 * HOUR
        val before = w.coinsInSystem()

        val outcome = w.source.cancelBooking("t1", "k1")

        assertEquals(CancelOutcome.Released(80), outcome)
        assertEquals(
            "po późnym odwołaniu tylko pacjent ma ścieżkę wypłaty; bez niej monety zostają w depozycie",
            EscrowModel.RELEASED,
            w.escrow("t1")!!.status
        )
        assertEquals(130, w.balance("p1"))
        assertEquals(120, w.balance("k1"))
        assertEquals(before, w.coinsInSystem())
        val earning = w.ledger("t1_p1")!!
        assertEquals(WalletTransactionType.BOOKING_EARNING, earning.type)
        assertEquals("k1", earning.counterpartyId)
        assertEquals("t1", userUpdates(w, "p1").single().fields["escrowSettlement"])
        assertEquals(1, w.source.transactions.committedBatches.count { batch ->
            batch.any { it.collection == FirestoreCollections.APPOINTMENTS && (it as? SettlementWrite.Update)?.fields?.get("status") == "CANCELLED" }
        })
    }

    @Test
    fun `psycholog nie wyplaci sobie depozytu z wizyty odwolanej przez pacjenta, wiec klient musi zrobic to w jednej partii`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.now = START + 3 * DAY - HOUR
        val partialCancel = listOf(
            SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, "t1", mapOf("status" to "CANCELLED", "cancelledBy" to "k1"))
        )
        w.attack("k1", partialCancel)
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)

        val release = listOf(
            SettlementWrite.Update(FirestoreCollections.ESCROWS, "t1", mapOf("status" to EscrowModel.RELEASED)),
            contactWrite("p1", mapOf("coinsBalance" to 130, "escrowSettlement" to "t1"))
        )
        failure { w.attack("p1", release) }

        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
        assertEquals(50, w.balance("p1"))
    }

    @Test
    fun `po zadnym odwolaniu depozyt nie zostaje HELD`() = runTest {
        val cases = listOf(
            Triple("k1", 0L, "pacjent wcześnie"),
            Triple("k1", 3 * DAY - 2 * HOUR, "pacjent późno"),
            Triple("p1", 3 * DAY - 2 * HOUR, "psycholog późno"),
            Triple("p1", 0L, "psycholog wcześnie")
        )
        cases.forEach { (who, offset, label) ->
            val w = world(slot("t1", "p1", 80))
            w.book()
            w.now = START + offset

            w.source.cancelBooking("t1", who)

            assertNotNull(label, w.escrow("t1"))
            assertTrue("$label: depozyt nie może zostać HELD po odwołaniu", w.escrow("t1")!!.status != EscrowModel.HELD)
            assertEquals("$label: monety nie mogą zniknąć ani się pojawić", 500, w.coinsInSystem())
        }
    }

    @Test
    fun `wizyta odwolana bez depozytu - sprzed wprowadzenia depozytow - nie rusza monet`() = runTest {
        val legacy = slot("t1", "p1", 80).copy(patientId = "k1", status = "BOOKED")
        val w = world(legacy)

        val outcome = w.source.cancelBooking("t1", "p1")

        assertEquals(CancelOutcome.CancelledWithoutRefund, outcome)
        assertEquals("CANCELLED", w.appointment("t1").status)
        assertEquals(200, w.balance("k1"))
        assertEquals(50, w.balance("p1"))
    }

    @Test
    fun `doladowanie pacjenta w trakcie zwrotu i zwrot daja sume obu, a zwrot nie wymaga ponowienia`() = runTest {
        suspend fun attemptsOfRefund(concurrentTopUp: Boolean): Pair<Int, EscrowTestWorld> {
            val w = world(slot("t1", "p1", 80))
            w.book()
            if (concurrentTopUp) w.source.transactions.beforeFirstCommit = { w.topUp("k1", 30) }
            val before = w.source.transactions.attempts
            w.source.cancelBooking("t1", "p1")
            return w.source.transactions.attempts - before to w
        }
        val (quietAttempts, _) = attemptsOfRefund(concurrentTopUp = false)
        val (attempts, w) = attemptsOfRefund(concurrentTopUp = true)

        assertEquals("przyrost dokłada się do salda zmienionego w trakcie: 120 + 30 + 80, a nie ostatnia zapisana wartość", 230, w.balance("k1"))
        assertEquals(EscrowModel.REFUNDED, w.escrow("t1")!!.status)
        assertEquals("równoczesna zmiana salda kontrahenta nie wymusza ponowienia", quietAttempts, attempts)
        assertEquals(500 + 30, w.coinsInSystem())
    }

    @Test
    fun `po 14 dniach od terminu pacjent odzyskuje monety, a wizyta zostaje BOOKED`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.now = START + 3 * DAY + 14 * DAY + 1
        val before = w.coinsInSystem()

        val outcome = w.source.refundAbandonedBooking("t1", "k1")

        assertEquals(CancelOutcome.Refunded(80), outcome)
        assertEquals(EscrowModel.REFUNDED, w.escrow("t1")!!.status)
        assertEquals(200, w.balance("k1"))
        assertEquals("BOOKED", w.appointment("t1").status)
        assertEquals(before, w.coinsInSystem())
        assertEquals("t1", userUpdates(w, "k1").last().fields["escrowSettlement"])
        assertEquals(WalletTransactionType.REFUND, w.ledger("t1_refund")!!.type)
    }

    @Test
    fun `przed uplywem 14 dni zwrot po oknie jest odrzucony`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.now = START + 3 * DAY + 14 * DAY

        failure { w.source.refundAbandonedBooking("t1", "k1") }

        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
        assertEquals(120, w.balance("k1"))
    }

    @Test
    fun `psycholog nie odzyska monet tym zwrotem`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.now = START + 3 * DAY + 20 * DAY

        failure { w.source.refundAbandonedBooking("t1", "p1") }

        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
    }

    @Test
    fun `po zwrocie po oknie psycholog nadal moze zakonczyc wizyte, ale niczego juz nie dostaje`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.now = START + 3 * DAY + 15 * DAY
        w.source.refundAbandonedBooking("t1", "k1")

        w.source.completeAppointment("t1")

        assertEquals("COMPLETED", w.appointment("t1").status)
        assertEquals(50, w.balance("p1"))
        assertEquals(200, w.balance("k1"))
        assertEquals(EscrowModel.REFUNDED, w.escrow("t1")!!.status)
        assertEquals(500, w.coinsInSystem())
    }

    @Test
    fun `po zakonczeniu przez psychologa zwrot po oknie nie wyplaci drugi raz`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.now = START + 3 * DAY + 15 * DAY
        w.source.completeAppointment("t1")

        failure { w.source.refundAbandonedBooking("t1", "k1") }

        assertEquals(130, w.balance("p1"))
        assertEquals(120, w.balance("k1"))
        assertEquals(500, w.coinsInSystem())
    }

    @Test
    fun `klient - pacjent odwoluje dokladnie 24 h przed terminem, depozyt idzie do psychologa`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.now = START + 3 * DAY - DAY

        val outcome = w.source.cancelBooking("t1", "k1")

        assertEquals(CancelOutcome.Released(80), outcome)
        assertEquals(EscrowModel.RELEASED, w.escrow("t1")!!.status)
        assertEquals(130, w.balance("p1"))
    }

    @Test
    fun `klient - pacjent odwoluje 24 h i milisekunde przed terminem, dostaje zwrot`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.now = START + 3 * DAY - DAY - 1

        val outcome = w.source.cancelBooking("t1", "k1")

        assertEquals(CancelOutcome.Refunded(80), outcome)
        assertEquals(EscrowModel.REFUNDED, w.escrow("t1")!!.status)
        assertEquals(200, w.balance("k1"))
    }

    @Test
    fun `klient - zwrot po oknie dokladnie 14 dni po terminie jest odrzucony, milisekunde pozniej przechodzi`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.now = START + 3 * DAY + 14 * DAY

        val refusal = failure { w.source.refundAbandonedBooking("t1", "k1") }
        assertTrue(
            "granicę 14 dni ma pilnować klient, a nie dopiero reguła: ${refusal.message}",
            refusal is UserMessageException
        )
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)

        w.now += 1
        assertEquals(CancelOutcome.Refunded(80), w.source.refundAbandonedBooking("t1", "k1"))
        assertEquals(200, w.balance("k1"))
    }

    @Test
    fun `usuniete konto psychologa - pozne odwolanie pacjenta nie odwoluje wizyty, a monety wracaja po oknie zwrotu`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.deleteAccount("p1")
        w.now = START + 3 * DAY - 2 * HOUR

        val error = failure { w.source.cancelBooking("t1", "k1") }

        assertTrue(error.message!!, error.message!!.contains("Konto psychologa nie istnieje"))
        assertTrue(error.message!!, error.message!!.contains("odzyskasz po upływie okna"))
        assertEquals(
            "wizyta musi zostać BOOKED: depozyt HELD przy wizycie CANCELLED byłby uwięziony na zawsze",
            "BOOKED",
            w.appointment("t1").status
        )
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)

        w.now = START + 3 * DAY + 14 * DAY + 1
        w.source.refundAbandonedBooking("t1", "k1")
        assertEquals(200, w.balance("k1"))
        assertEquals(EscrowModel.REFUNDED, w.escrow("t1")!!.status)
    }

    @Test
    fun `usuniete konto pacjenta - odwolanie przez psychologa odmawia komunikatem o koncie pacjenta i o skutku zakonczenia`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.deleteAccount("k1")

        val error = failure { w.source.cancelBooking("t1", "p1") }

        assertTrue(error.message!!, error.message!!.contains("Konto pacjenta nie istnieje"))
        assertTrue(error.message!!, error.message!!.contains("przypisze monety Tobie"))
        assertEquals("BOOKED", w.appointment("t1").status)
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
    }

    private fun assertOutcomeOnlyMessage(error: Throwable) {
        assertTrue("błąd miał być komunikatem dla użytkownika, a był: $error", error is UserMessageException)
        val text = error.message!!
        assertTrue(
            "interfejs podaje przyczynę, której nie zna: odmowa reguł nie mówi, czy konto drugiej strony istnieje ($text)",
            !text.contains("nie istnieje")
        )
        assertTrue(text, text.contains("Nie udało się rozliczyć monet z drugą stroną"))
        assertTrue(text, text.contains("wizyta pozostaje zarezerwowana"))
        assertTrue(text, text.contains("Monety pozostają w depozycie tej wizyty"))
        assertTrue("komunikat obiecuje zwrot, który nie przysługuje każdej stronie i nie nadejdzie, gdy konto pacjenta znika ($text)", !text.contains("wrócą"))
    }

    @Test
    fun `odmowa przy rozliczeniu z pacjentem - dokument prywatny znikl - komunikat mowi o skutku, nie o koncie`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.users.privateProfiles.profiles.value = w.users.privateProfiles.profiles.value - "k1"

        assertOutcomeOnlyMessage(failure { w.source.cancelBooking("t1", "p1") })

        assertEquals("BOOKED", w.appointment("t1").status)
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
    }

    @Test
    fun `odmowa przy rozliczeniu z psychologiem - dokument prywatny znikl - komunikat mowi o skutku, nie o koncie`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.users.privateProfiles.profiles.value = w.users.privateProfiles.profiles.value - "p1"
        w.now = START + 3 * DAY - 2 * HOUR

        assertOutcomeOnlyMessage(failure { w.source.cancelBooking("t1", "k1") })

        assertEquals("BOOKED", w.appointment("t1").status)
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
    }

    @Test
    fun `odmowa z innej przyczyny niz brak dokumentu - rozjazd zegarow na granicy 24 h - dostaje ten sam komunikat o skutku`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.now = START + 3 * DAY - 2 * HOUR
        w.source.transactions.beforeFirstCommit = { w.now = START }

        assertOutcomeOnlyMessage(failure { w.source.cancelBooking("t1", "k1") })

        assertEquals("BOOKED", w.appointment("t1").status)
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
        assertEquals(500, w.coinsInSystem())
    }

    @Test
    fun `brak WLASNEGO dokumentu prywatnego przy zwrocie nie jest podszywany pod brak konta drugiej strony`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.users.privateProfiles.profiles.value = w.users.privateProfiles.profiles.value - "k1"

        val error = failure { w.source.cancelBooking("t1", "k1") }

        assertTrue("własny zwrot nie jest odwołaniem względem cudzego konta: ${error.message}", error !is UserMessageException)
        assertTrue(error.message!!, error.message!!.startsWith("PERMISSION_DENIED"))
        assertEquals("BOOKED", w.appointment("t1").status)
    }

    @Test
    fun `usuniete konto psychologa nie blokuje wczesnego odwolania pacjenta, bo zwrot nie dotyka psychologa`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.book()
        w.deleteAccount("p1")

        val outcome = w.source.cancelBooking("t1", "k1")

        assertEquals(CancelOutcome.Refunded(80), outcome)
        assertEquals("CANCELLED", w.appointment("t1").status)
        assertEquals(200, w.balance("k1"))
    }

    @Test
    fun `wystawienie terminu z cena rozna od ceny w profilu jest odrzucone tak jak przez regule`() = runTest {
        val w = world()
        w.users.users.value = w.users.users.value.map { if (it.id == "p1") it.copy(pricePerSession = 80.0) else it }

        failure { w.source.saveAppointment(slot("t1", "p1", 70)) }
        assertTrue(w.source.appointments.value.isEmpty())

        w.source.saveAppointment(slot("t1", "p1", 80))
        assertEquals(1, w.source.appointments.value.size)
    }

    @Test
    fun `wystawienie terminu w przeszlosci albo przez pacjenta jest odrzucone`() = runTest {
        val w = world()
        w.users.users.value = w.users.users.value.map { it.copy(pricePerSession = 80.0) }

        failure { w.source.saveAppointment(slot("t1", "p1", 80, startsAt = START - HOUR)) }
        failure { w.source.saveAppointment(slot("t2", "k1", 80)) }

        assertTrue(w.source.appointments.value.isEmpty())
    }

    @Test
    fun `usuniecie zajetego terminu jest odrzucone, wolnego przechodzi`() = runTest {
        val w = world(slot("t1", "p1", 80), slot("t2", "p1", 50))
        w.book()

        failure { w.source.deleteAppointment("t1") }
        w.source.deleteAppointment("t2")

        assertEquals(listOf("t1"), w.source.appointments.value.map { it.appointmentId })
    }

    @Test
    fun `psycholog nie wystawi terminu w imieniu innego psychologa`() = runTest {
        val w = world()
        w.users.users.value = w.users.users.value.map { it.copy(pricePerSession = 80.0) }
        w.source.signedInUserId = "p2"

        val error = failure { w.source.saveAppointment(slot("t1", "p1", 80)) }

        assertTrue(error.message!!, error.message!!.contains("wystawia wyłącznie jego psycholog"))
        assertTrue(w.source.appointments.value.isEmpty())
    }

    @Test
    fun `pacjent nie skasuje cudzego wolnego terminu`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.source.signedInUserId = "k1"

        failure { w.source.deleteAppointment("t1") }

        assertEquals(listOf("t1"), w.source.appointments.value.map { it.appointmentId })
    }

    @Test
    fun `kasowanie nieistniejacego terminu jest odrzucone tak jak przez regule`() = runTest {
        val w = world(slot("t1", "p1", 80))

        failure { w.source.deleteAppointment("brak") }

        assertEquals(listOf("t1"), w.source.appointments.value.map { it.appointmentId })
    }

    @Test
    fun `fixtura swiata nie moze zawierac terminu, ktorego regula by nie przyjela`() {
        val error = try {
            EscrowTestWorld(slots = listOf(slot("t1", "p1", -5)))
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertTrue(error?.message ?: "fixtura z ujemną ceną została przyjęta", error?.message?.contains("nie mogłaby powstać w produkcji") == true)
    }
}
