package com.example.stellare.appointments

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.EscrowModel
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Saldo zmienia się wyłącznie o przyrost (FieldValue.increment), bo strona rozliczająca nie może
 * odczytać dokumentu prywatnego kontrahenta i nie policzy wartości bezwzględnej. Przyrost jest
 * tą samą wartością, którą kilka depozytów może próbować wykorzystać naraz, więc testy tej klasy
 * składają partie, w których JEDEN przyrost obsługuje kilka depozytów, i sprawdzają SUMĘ monet
 * w systemie, a nie stan pojedynczego konta.
 */
class EscrowIncrementTest {

    private val total = 500

    private fun world(vararg slots: AppointmentModel) = EscrowTestWorld(slots = slots.toList())

    private fun delta(userId: String, amount: Int, declaration: String?): SettlementWrite = contactWrite(
        userId,
        buildMap {
            put("coinsBalance", BalanceDelta(amount))
            if (declaration != null) put("escrowSettlement", declaration)
        }
    )

    private fun bookingWithoutBalance(slot: AppointmentModel, patient: String): List<SettlementWrite> = listOf(
        SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, slot.appointmentId, mapOf("patientId" to patient, "status" to "BOOKED", "notes" to "")),
        SettlementWrite.Create(FirestoreCollections.ESCROWS, slot.appointmentId, EscrowModel(slot.appointmentId, patient, slot.psychologistId, slot.priceCoins))
    )

    private fun completion(id: String) =
        SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, id, mapOf("status" to "COMPLETED"))

    private fun escrowStatus(id: String, status: String) =
        SettlementWrite.Update(FirestoreCollections.ESCROWS, id, mapOf("status" to status))

    private suspend fun rejected(w: EscrowTestWorld, actor: String, writes: List<SettlementWrite>, why: String) {
        val before = w.coinsInSystem()
        try {
            w.attack(actor, writes)
        } catch (e: IllegalStateException) {
            assertTrue("odmowa miała być z reguł, a była: ${e.message}", e.message!!.startsWith("PERMISSION_DENIED"))
            assertEquals("odmowa zmieniła sumę monet: $why", before, w.coinsInSystem())
            return
        }
        fail("partia przeszła, choć nie powinna: $why. Suma monet w systemie: ${w.coinsInSystem()} zamiast $before")
    }

    private fun assertConserved(w: EscrowTestWorld, what: String) =
        assertEquals("$what: monety zostały wybite albo zniszczone", total, w.coinsInSystem())

    private suspend fun bookedPair(w: EscrowTestWorld) {
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.source.bookAppointmentAtomically("t2", "k2", "p1", 80, "")
        w.now = START + 3 * DAY + HOUR
    }

    @Test
    fun `kontrola - rezerwacja przyrostem o minus cene z deklaracja przechodzi`() = runTest {
        val t1 = slot("t1", "p1", 80)
        val w = world(t1)

        w.attack("k1", bookingWithoutBalance(t1, "k1") + delta("k1", -80, "t1"))

        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
        assertEquals(120, w.balance("k1"))
        assertConserved(w, "poprawna rezerwacja przyrostem")
    }

    @Test
    fun `kontrola - wyplata psychologowi przyrostem o plus kwote depozytu z deklaracja przechodzi, drugi depozyt zostaje`() = runTest {
        val w = world(slot("t1", "p1", 80), slot("t2", "p1", 80, hour = 11))
        bookedPair(w)

        w.attack("p1", listOf(completion("t1"), escrowStatus("t1", EscrowModel.RELEASED), delta("p1", 80, "t1")))

        assertEquals(EscrowModel.RELEASED, w.escrow("t1")!!.status)
        assertEquals(EscrowModel.HELD, w.escrow("t2")!!.status)
        assertEquals(130, w.balance("p1"))
        assertConserved(w, "poprawna wypłata przyrostem")
    }

    @Test
    fun `N rezerwacji jednym przyrostem o minus cene - odmowa, suma niezmieniona`() = runTest {
        listOf(2, 3).forEach { count ->
            listOf("t1", "t2", "t3", null).forEach { declaration ->
                val slots = (1..count).map { slot("t$it", "p1", 80, hour = 9 + it) }
                val w = world(*slots.toTypedArray())
                val batch = slots.flatMap { bookingWithoutBalance(it, "k1") } + delta("k1", -80, declaration)

                rejected(w, "k1", batch, "$count depozytów po 80 za jeden spadek salda o 80 (deklaracja $declaration)")

                slots.forEach { assertEquals("FREE", w.appointment(it.appointmentId).status) }
                assertEquals(200, w.balance("k1"))
                assertConserved(w, "$count rezerwacji jednym przyrostem")
            }
        }
    }

    @Test
    fun `dwie rezerwacje podwojonym przyrostem i jedna deklaracja - odmowa`() = runTest {
        val t1 = slot("t1", "p1", 80)
        val t2 = slot("t2", "p1", 80, hour = 11)
        val w = world(t1, t2)

        rejected(
            w, "k1",
            bookingWithoutBalance(t1, "k1") + bookingWithoutBalance(t2, "k1") + delta("k1", -160, "t1"),
            "spadek o 160 spełnia warunek salda tylko jednego z dwóch depozytów"
        )
        assertConserved(w, "podwojony spadek przyrostem")
    }

    @Test
    fun `rezerwacja z przyrostem mniejszym niz cena - bicie monet - odmowa`() = runTest {
        val t1 = slot("t1", "p1", 80)
        val w = world(t1)

        rejected(w, "k1", bookingWithoutBalance(t1, "k1") + delta("k1", -10, "t1"), "depozyt 80 za spadek salda o 10")
        assertConserved(w, "niepełny spadek salda")
    }

    @Test
    fun `dwa rozliczenia jednym przyrostem o plus kwote depozytu - odmowa, oba depozyty zostaja HELD`() = runTest {
        listOf("t1", "t2", null).forEach { declaration ->
            val w = world(slot("t1", "p1", 80), slot("t2", "p1", 80, hour = 11))
            bookedPair(w)
            val batch = listOf(
                completion("t1"), completion("t2"),
                escrowStatus("t1", EscrowModel.RELEASED), escrowStatus("t2", EscrowModel.RELEASED),
                delta("p1", 80, declaration)
            )

            rejected(w, "p1", batch, "dwa depozyty po 80 zwolnione jednym wzrostem o 80 (deklaracja $declaration)")

            assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
            assertEquals(EscrowModel.HELD, w.escrow("t2")!!.status)
            assertEquals(50, w.balance("p1"))
            assertConserved(w, "dwa rozliczenia jednym przyrostem (deklaracja $declaration)")
        }
    }

    @Test
    fun `dwa rozliczenia podwojonym przyrostem i jedna deklaracja - odmowa`() = runTest {
        val w = world(slot("t1", "p1", 80), slot("t2", "p1", 80, hour = 11))
        bookedPair(w)
        val batch = listOf(
            completion("t1"), completion("t2"),
            escrowStatus("t1", EscrowModel.RELEASED), escrowStatus("t2", EscrowModel.RELEASED),
            delta("p1", 160, "t1")
        )

        rejected(w, "p1", batch, "wzrost o 160 z deklaracją jednego z dwóch depozytów")
    }

    @Test
    fun `dwa przyrosty na tym samym dokumencie w jednej partii - odmowa, zeby dwa depozyty nie dzielily jednej deklaracji`() = runTest {
        val w = world(slot("t1", "p1", 80), slot("t2", "p1", 80, hour = 11))
        bookedPair(w)
        val batch = listOf(
            completion("t1"), completion("t2"),
            escrowStatus("t1", EscrowModel.RELEASED), escrowStatus("t2", EscrowModel.RELEASED),
            delta("p1", 80, "t1"), delta("p1", 80, "t2")
        )

        rejected(w, "p1", batch, "dwa osobne przyrosty po 80 na jednym dokumencie z różnymi deklaracjami")
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
        assertEquals(EscrowModel.HELD, w.escrow("t2")!!.status)
    }

    private fun cancelledByPsychologist(id: String) =
        SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, id, mapOf("status" to "CANCELLED", "cancelledBy" to "p1"))

    private suspend fun twoBookingsOfOnePatient(): EscrowTestWorld {
        val w = world(slot("t1", "p1", 80), slot("t2", "p1", 80, hour = 11))
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.source.bookAppointmentAtomically("t2", "k1", "p1", 80, "")
        return w
    }

    @Test
    fun `dwa zwroty jednemu pacjentowi jednym przyrostem o plus kwote depozytu - odmowa, oba depozyty zostaja HELD`() = runTest {
        listOf("t1", "t2", null).forEach { declaration ->
            val w = twoBookingsOfOnePatient()
            val batch = listOf(
                cancelledByPsychologist("t1"), cancelledByPsychologist("t2"),
                escrowStatus("t1", EscrowModel.REFUNDED), escrowStatus("t2", EscrowModel.REFUNDED),
                delta("k1", 80, declaration)
            )

            rejected(w, "p1", batch, "dwa zwroty po 80 jednemu pacjentowi jednym wzrostem o 80 (deklaracja $declaration)")

            assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
            assertEquals(EscrowModel.HELD, w.escrow("t2")!!.status)
            assertEquals(40, w.balance("k1"))
            assertConserved(w, "dwa zwroty jednym przyrostem (deklaracja $declaration)")
        }
    }

    @Test
    fun `dwa zwroty podwojonym przyrostem i jedna deklaracja - odmowa`() = runTest {
        val w = twoBookingsOfOnePatient()
        val batch = listOf(
            cancelledByPsychologist("t1"), cancelledByPsychologist("t2"),
            escrowStatus("t1", EscrowModel.REFUNDED), escrowStatus("t2", EscrowModel.REFUNDED),
            delta("k1", 160, "t1")
        )

        rejected(w, "p1", batch, "przyrost o 160 z deklaracją jednego z dwóch zwracanych depozytów")
        assertConserved(w, "podwojony zwrot")
    }

    @Test
    fun `kontrola - zwrot pojedynczego depozytu z tych samych klockow przechodzi, drugi zostaje`() = runTest {
        val w = twoBookingsOfOnePatient()

        w.attack("p1", listOf(cancelledByPsychologist("t1"), escrowStatus("t1", EscrowModel.REFUNDED), delta("k1", 80, "t1")))

        assertEquals(EscrowModel.REFUNDED, w.escrow("t1")!!.status)
        assertEquals(EscrowModel.HELD, w.escrow("t2")!!.status)
        assertEquals(120, w.balance("k1"))
        assertConserved(w, "poprawny zwrot przyrostem")
    }

    @Test
    fun `rozliczenie z przyrostem dla wspolnika z tego samego depozytu - odmowa`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.now = START + 3 * DAY + HOUR

        rejected(
            w, "p1",
            listOf(completion("t1"), escrowStatus("t1", EscrowModel.RELEASED), delta("p1", 80, "t1"), delta("k2", 80, "t1")),
            "wypłata plus 80 monet dla wspólnika z tego samego depozytu"
        )
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
        assertEquals(200, w.balance("k2"))
    }

    @Test
    fun `przyrost dla obcego konta z deklaracja czyjegos depozytu - odmowa`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.now = START + 3 * DAY + HOUR

        rejected(w, "k2", listOf(escrowStatus("t1", EscrowModel.REFUNDED), delta("k2", 80, "t1")), "obcy zwraca sobie cudzy depozyt")
        rejected(w, "k2", listOf(delta("p1", 80, "t1")), "obcy podnosi saldo psychologa na podstawie nierozliczonego depozytu")
    }

    @Test
    fun `rozliczenie krzyzowe tej samej osoby jako pacjenta i psychologa jednym przyrostem - odmowa`() = runTest {
        val w = EscrowTestWorld(
            psychologists = mapOf("p1" to 100, "p2" to 50),
            slots = listOf(slot("t1", "p1", 80), slot("t3", "p2", 80, hour = 12))
        )
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.source.bookAppointmentAtomically("t3", "p1", "p2", 80, "")
        w.now = START + 3 * DAY + HOUR
        val before = w.coinsInSystem()
        val crossBatch = listOf(
            completion("t1"),
            escrowStatus("t1", EscrowModel.RELEASED),
            SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, "t3", mapOf("status" to "CANCELLED", "cancelledBy" to "p2")),
            escrowStatus("t3", EscrowModel.REFUNDED),
            delta("p1", 160, "t1")
        )

        rejected(w, "p1", crossBatch, "jeden przyrost o 160 spłaca depozyt psychologa i zwrot pacjenta")

        assertEquals(before, w.coinsInSystem())
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
        assertEquals(EscrowModel.HELD, w.escrow("t3")!!.status)
    }

    @Test
    fun `cudzy przyrost na podstawie deklaracji juz rozliczonego depozytu - odmowa`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.now = START + 3 * DAY + HOUR
        w.source.completeAppointment("t1")
        assertEquals(130, w.balance("p1"))

        rejected(w, "k2", listOf(delta("p1", 80, "t1")), "obcy podnosi cudze saldo na starej deklaracji")
        rejected(w, "k1", listOf(delta("p1", 80, "t1")), "druga strona dawnego depozytu podnosi saldo psychologa na starej deklaracji")
        assertEquals(130, w.balance("p1"))
    }

    @Test
    fun `doladowanie nie przykrywa rozliczenia - przyrost wiekszy niz depozyt w partii z wyplata jest odrzucony`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.now = START + 3 * DAY + HOUR

        rejected(
            w, "p1",
            listOf(completion("t1"), escrowStatus("t1", EscrowModel.RELEASED), delta("p1", 580, "t1")),
            "wypłata 80 z doładowaniem 500 w jednym przyroście"
        )
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
    }

    @Test
    fun `doladowanie do limitu przechodzi i jest jedynym wyjatkiem od stalosci sumy`() = runTest {
        val w = world(slot("t1", "p1", 80))

        w.attack("k1", listOf(delta("k1", 1000, null)))
        assertEquals(total + 1000, w.coinsInSystem())

        rejected(w, "k1", listOf(delta("k1", 1001, null)), "doładowanie ponad limit 1000 jednym przyrostem")
    }

    @Test
    fun `dobrowolne zniszczenie wlasnych monet przyrostem ujemnym przechodzi, cudzych nie`() = runTest {
        val w = world(slot("t1", "p1", 80))

        w.attack("k1", listOf(delta("k1", -50, null)))
        assertEquals(total - 50, w.coinsInSystem())

        rejected(w, "k2", listOf(delta("k1", -50, null)), "obcy niszczy cudze monety")
    }

    @Test
    fun `strona rozliczajaca nie moze odczytac salda kontrahenta`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.source.transactions.actor = "p1"

        val denial = try {
            w.source.transactions.run { tx -> tx.readBalance("k1") }
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertTrue("odczyt cudzego dokumentu prywatnego musi dostać odmowę: ${denial?.message}", denial?.message?.startsWith("PERMISSION_DENIED") == true)
        assertEquals("odczyt własnego salda przechodzi", 50, w.source.transactions.run { tx -> tx.readBalance("p1") })
    }

    @Test
    fun `wyplata i zwrot nie czytaja salda kontrahenta, wiec przechodza mimo zakazu odczytu`() = runTest {
        val w = world(slot("t1", "p1", 80), slot("t2", "p1", 80, hour = 11))
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.source.bookAppointmentAtomically("t2", "k2", "p1", 80, "")

        w.source.cancelBooking("t1", "p1")
        assertEquals("zwrot zainicjowany przez psychologa, który nie widzi salda pacjenta", 200, w.balance("k1"))

        w.now = START + 3 * DAY - 2 * HOUR
        w.source.cancelBooking("t2", "k2")
        assertEquals("wypłata zainicjowana przez pacjenta, który nie widzi salda psychologa", 130, w.balance("p1"))
        assertEquals(0, w.heldTotal())
        assertConserved(w, "zwrot i wypłata inicjowane przez drugą stronę")
    }

    @Test
    fun `kazdy zapis salda wykonany przez produkcyjny kod jest przyrostem, nigdy wartoscia bezwzgledna`() = runTest {
        val w = world(
            slot("t1", "p1", 80), slot("t2", "p1", 80, hour = 11), slot("t3", "p1", 60, hour = 12),
            slot("t4", "p1", 40, hour = 13)
        )
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.source.bookAppointmentAtomically("t2", "k2", "p1", 80, "")
        w.source.bookAppointmentAtomically("t3", "k1", "p1", 60, "")
        w.source.bookAppointmentAtomically("t4", "k2", "p1", 40, "")
        w.source.cancelBooking("t1", "k1")
        w.now = START + 3 * DAY - 2 * HOUR
        w.source.cancelBooking("t2", "k2")
        w.now = START + 3 * DAY + HOUR
        w.source.completeAppointment("t3")
        w.now = START + 3 * DAY + 15 * DAY
        w.source.refundAbandonedBooking("t4", "k2")

        val balanceWrites = w.source.transactions.committedBatches.flatten()
            .filterIsInstance<SettlementWrite.Update>()
            .filter { it.collection.endsWith("/${FirestoreCollections.PRIVATE}") && "coinsBalance" in it.fields }
        assertTrue("za mało zapisów salda, by asercja coś znaczyła: ${balanceWrites.size}", balanceWrites.size >= 8)
        balanceWrites.forEach { write ->
            assertTrue(
                "zapis salda ${write.id} użytkownika ${write.collection} jest wartością bezwzględną ${write.fields["coinsBalance"]}: " +
                    "strona rozliczająca musiałaby odczytać cudzy dokument prywatny, dostać odmowę, a monety zostałyby w depozycie",
                write.fields["coinsBalance"] is BalanceDelta
            )
        }
        val sumOfDeltas = balanceWrites.sumOf { (it.fields["coinsBalance"] as BalanceDelta).amount }
        assertEquals("wszystkie depozyty rozliczone, więc przyrosty płatności i rozliczeń muszą się wyzerować", 0, sumOfDeltas)
        assertEquals(0, w.heldTotal())
        assertConserved(w, "scenariusz z każdą ścieżką rozliczenia")
    }
}
