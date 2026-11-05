package com.example.stellare.appointments

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.UserMessageException
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
import kotlin.random.Random

/**
 * Niezmiennik w dokładnym brzmieniu: suma sald plus suma depozytów HELD jest stała, z wyjątkiem
 * doładowania (do limitu) i dobrowolnego zniszczenia własnych monet. Doładowanie może zajść
 * w tej samej partii co rozliczenie depozytu. Testy tej klasy nadużywają JEDNEJ wartości
 * (jednego ruchu salda, jednej deklaracji, jednego depozytu) przez kilka dokumentów naraz
 * i sprawdzają SUMĘ, a nie stan pojedynczego konta: warunek pojedynczego dokumentu przechodzi
 * dla ataku, który tylko przenosi monety w złe miejsce.
 */
class EscrowMultiDocumentTest {

    private val total = 500

    private fun world(vararg slots: AppointmentModel) = EscrowTestWorld(slots = slots.toList())

    private fun crossWorld() = EscrowTestWorld(
        psychologists = mapOf("p1" to 100, "p2" to 50),
        slots = listOf(slot("t1", "p1", 80), slot("t3", "p2", 80, hour = 12))
    )

    private fun bookingBatch(
        slot: AppointmentModel,
        patient: String,
        newBalance: Int,
        declaration: String?,
        escrowAmount: Int = slot.priceCoins
    ): List<SettlementWrite> = listOf(
        SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, slot.appointmentId, mapOf("patientId" to patient, "status" to "BOOKED", "notes" to "")),
        SettlementWrite.Create(FirestoreCollections.ESCROWS, slot.appointmentId, EscrowModel(slot.appointmentId, patient, slot.psychologistId, escrowAmount))
    ) + userWrite(patient, newBalance, declaration)

    private fun userWrite(userId: String, newBalance: Int, declaration: String?): List<SettlementWrite> = listOf(
        contactWrite(
            userId,
            buildMap {
                put("coinsBalance", newBalance)
                if (declaration != null) put("escrowSettlement", declaration)
            }
        )
    )

    private fun completion(id: String, newStatus: String = "COMPLETED") =
        SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, id, mapOf("status" to newStatus))

    private fun escrowStatus(id: String, status: String) =
        SettlementWrite.Update(FirestoreCollections.ESCROWS, id, mapOf("status" to status))

    private suspend fun rejected(world: EscrowTestWorld, actor: String, writes: List<SettlementWrite>, why: String) {
        try {
            world.attack(actor, writes)
        } catch (e: IllegalStateException) {
            assertTrue("odmowa miała być z reguł, a była: ${e.message}", e.message!!.startsWith("PERMISSION_DENIED"))
            return
        }
        fail("partia przeszła, choć nie powinna: $why. Suma monet w systemie: ${world.coinsInSystem()} zamiast $total")
    }

    private fun assertCoinsConserved(world: EscrowTestWorld, what: String) =
        assertEquals("$what: monety zostały wybite albo zniszczone", total, world.coinsInSystem())

    @Test
    fun `kontrola - pojedyncza poprawna rezerwacja zlozona z tych samych klockow przechodzi`() = runTest {
        val t1 = slot("t1", "p1", 80)
        val w = world(t1)

        w.attack("k1", bookingBatch(t1, "k1", newBalance = 120, declaration = "t1"))

        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
        assertCoinsConserved(w, "poprawna rezerwacja")
    }

    @Test
    fun `dwie rezerwacje jednym ruchem salda - odmowa, suma niezmieniona`() = runTest {
        val t1 = slot("t1", "p1", 80)
        val t2 = slot("t2", "p1", 80, hour = 11)
        listOf("t1", "t2", null).forEach { declaration ->
            val w = world(t1, t2)
            val twoBookingsOnePayment = bookingBatch(t1, "k1", newBalance = 120, declaration = declaration) +
                bookingBatch(t2, "k1", newBalance = 120, declaration = declaration).filterNot { it.collection == FirestoreCollections.privateOf("k1") }

            rejected(w, "k1", twoBookingsOnePayment, "dwa depozyty po 80 za jeden spadek salda o 80, deklaracja=$declaration")

            assertCoinsConserved(w, "dwie rezerwacje jednym ruchem salda (deklaracja $declaration)")
            assertEquals("FREE", w.appointment("t1").status)
            assertEquals("FREE", w.appointment("t2").status)
            assertEquals(200, w.balance("k1"))
        }
    }

    @Test
    fun `dwie rezerwacje z podwojonym spadkiem salda i jedna deklaracja - odmowa`() = runTest {
        val t1 = slot("t1", "p1", 80)
        val t2 = slot("t2", "p1", 80, hour = 11)
        val w = world(t1, t2)
        val batch = bookingBatch(t1, "k1", newBalance = 40, declaration = "t1") +
            bookingBatch(t2, "k1", newBalance = 40, declaration = "t1").filterNot { it.collection == FirestoreCollections.privateOf("k1") }

        rejected(w, "k1", batch, "spadek o 160 spełnia warunek salda tylko jednego z dwóch depozytów")

        assertCoinsConserved(w, "podwojony spadek salda")
        assertEquals(200, w.balance("k1"))
    }

    @Test
    fun `depozyt wiekszy niz spadek salda - bicie monet - odmowa`() = runTest {
        val t1 = slot("t1", "p1", 80)
        val w = world(t1)

        rejected(w, "k1", bookingBatch(t1, "k1", newBalance = 190, declaration = "t1"), "depozyt 80 za spadek salda o 10")

        assertCoinsConserved(w, "depozyt bez pełnego zmniejszenia salda pacjenta to bicie monet")
    }

    @Test
    fun `platny termin zajety bez depozytu - odmowa`() = runTest {
        val t1 = slot("t1", "p1", 80)
        val w = world(t1)
        val batch = listOf(
            SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, "t1", mapOf("patientId" to "k1", "status" to "BOOKED", "notes" to ""))
        )

        rejected(w, "k1", batch, "termin za 80 monet zarezerwowany za darmo")

        assertEquals("FREE", w.appointment("t1").status)
        assertCoinsConserved(w, "rezerwacja bez depozytu")
    }

    private suspend fun bookedPair(w: EscrowTestWorld) {
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.source.bookAppointmentAtomically("t2", "k2", "p1", 80, "")
        w.now = START + 3 * DAY + HOUR
    }

    @Test
    fun `kontrola - pojedyncze rozliczenie zlozone z tych samych klockow przechodzi`() = runTest {
        val w = world(slot("t1", "p1", 80), slot("t2", "p1", 80, hour = 11))
        bookedPair(w)

        w.attack(
            "p1",
            listOf(completion("t1"), escrowStatus("t1", EscrowModel.RELEASED)) + userWrite("p1", 130, "t1")
        )

        assertEquals(EscrowModel.RELEASED, w.escrow("t1")!!.status)
        assertEquals(EscrowModel.HELD, w.escrow("t2")!!.status)
        assertCoinsConserved(w, "poprawne rozliczenie")
    }

    @Test
    fun `dwa rozliczenia jednym wzrostem salda - odmowa, drugi depozyt zostaje HELD`() = runTest {
        listOf("t1", "t2").forEach { declaration ->
            val w = world(slot("t1", "p1", 80), slot("t2", "p1", 80, hour = 11))
            bookedPair(w)
            val batch = listOf(
                completion("t1"), completion("t2"),
                escrowStatus("t1", EscrowModel.RELEASED), escrowStatus("t2", EscrowModel.RELEASED)
            ) + userWrite("p1", 130, declaration)

            rejected(w, "p1", batch, "dwa depozyty zwolnione jednym wzrostem o 80 (deklaracja $declaration)")

            assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
            assertEquals(EscrowModel.HELD, w.escrow("t2")!!.status)
            assertCoinsConserved(w, "dwa rozliczenia jednym wzrostem salda")
        }
    }

    @Test
    fun `dwa rozliczenia podwojonym wzrostem salda z jedna deklaracja - odmowa`() = runTest {
        val w = world(slot("t1", "p1", 80), slot("t2", "p1", 80, hour = 11))
        bookedPair(w)
        val batch = listOf(
            completion("t1"), completion("t2"),
            escrowStatus("t1", EscrowModel.RELEASED), escrowStatus("t2", EscrowModel.RELEASED)
        ) + userWrite("p1", 210, "t1")

        rejected(w, "p1", batch, "wzrost o 160 z deklaracją jednego z dwóch depozytów")

        assertCoinsConserved(w, "podwojony wzrost salda")
        assertEquals(50, w.balance("p1"))
    }

    @Test
    fun `rozliczenie krzyzowe tej samej osoby jako pacjenta i psychologa jedna deklaracja - odmowa`() = runTest {
        val w = crossWorld()
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.source.bookAppointmentAtomically("t3", "p1", "p2", 80, "")
        w.now = START + 3 * DAY + HOUR
        val totalBefore = w.coinsInSystem()
        val before = w.balance("p1")
        val crossBatch = listOf(
            completion("t1"),
            escrowStatus("t1", EscrowModel.RELEASED),
            SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, "t3", mapOf("status" to "CANCELLED", "cancelledBy" to "p2")),
            escrowStatus("t3", EscrowModel.REFUNDED)
        ) + userWrite("p1", before + 160, "t1")

        rejected(w, "p1", crossBatch, "jeden wzrost o 160 spłaca depozyt psychologa i zwrot pacjenta")

        assertEquals("rozliczenie krzyżowe: monety zostały wybite albo zniszczone", totalBefore, w.coinsInSystem())
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
        assertEquals(EscrowModel.HELD, w.escrow("t3")!!.status)
    }

    @Test
    fun `to samo rozliczenie krzyzowe rozbite na dwie transakcje przechodzi i zachowuje monety`() = runTest {
        val w = crossWorld()
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.source.bookAppointmentAtomically("t3", "p1", "p2", 80, "")
        w.now = START + 3 * DAY + HOUR
        val totalBefore = w.coinsInSystem()
        val before = w.balance("p1")

        w.source.completeAppointment("t1")
        w.source.cancelBooking("t3", "p2")

        assertEquals(before + 160, w.balance("p1"))
        assertEquals("rozbite rozliczenie krzyżowe: monety zostały wybite albo zniszczone", totalBefore, w.coinsInSystem())
        assertEquals(0, w.heldTotal())
    }

    @Test
    fun `jeden depozyt kredytuje strone rozliczajaca i wspolnika naraz - odmowa`() = runTest {
        val cancelByPsychologist = SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, "t1", mapOf("status" to "CANCELLED", "cancelledBy" to "p1"))
        val cancelByPatient = SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, "t1", mapOf("status" to "CANCELLED", "cancelledBy" to "k1"))
        val scenarios = listOf(
            Triple("p1", listOf(cancelByPsychologist, escrowStatus("t1", EscrowModel.REFUNDED)) + userWrite("k1", 200, "t1"), "zwrot pacjentowi"),
            Triple("k1", listOf(cancelByPatient, escrowStatus("t1", EscrowModel.RELEASED)) + userWrite("p1", 130, "t1"), "wypłata psychologowi")
        )
        scenarios.forEach { (actor, legitimate, label) ->
            val w = world(slot("t1", "p1", 80))
            w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
            w.now = START + 3 * DAY - 2 * HOUR
            val withAccomplice = legitimate + userWrite("k2", 280, "t1")

            rejected(w, actor, withAccomplice, "$label plus 80 monet dla wspólnika z tego samego depozytu")

            assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
            assertEquals(200, w.balance("k2"))
            assertCoinsConserved(w, "jeden depozyt dla dwóch odbiorców ($label)")
        }
    }

    @Test
    fun `depozyt wyplacony psychologowi nie moze byc potem zwrocony pacjentowi`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.now = START + 3 * DAY - 2 * HOUR
        w.source.cancelBooking("t1", "k1")
        assertEquals(EscrowModel.RELEASED, w.escrow("t1")!!.status)

        rejected(w, "p1", listOf(escrowStatus("t1", EscrowModel.REFUNDED)) + userWrite("k1", 200, "t1"), "ten sam depozyt wypłacony i zwrócony")

        assertEquals(EscrowModel.RELEASED, w.escrow("t1")!!.status)
        assertEquals(120, w.balance("k1"))
        assertCoinsConserved(w, "wypłata, a potem zwrot tego samego depozytu")
    }

    @Test
    fun `zwrot po oknie powtorzony na juz zwroconym depozycie - odmowa, jedyna bariera to status depozytu`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.now = START + 3 * DAY + 15 * DAY
        w.source.refundAbandonedBooking("t1", "k1")
        assertEquals(200, w.balance("k1"))

        rejected(
            w,
            "k1",
            listOf(escrowStatus("t1", EscrowModel.REFUNDED)) + userWrite("k1", 280, "t1"),
            "ten sam depozyt zwrócony drugi raz po oknie 14 dni"
        )

        assertEquals(200, w.balance("k1"))
        assertCoinsConserved(w, "drugi zwrot po oknie")
    }

    @Test
    fun `stara deklaracja nie upowaznia nikogo do podniesienia cudzego salda po rozliczeniu`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.now = START + 3 * DAY + HOUR
        w.source.completeAppointment("t1")
        assertEquals(130, w.balance("p1"))

        rejected(w, "k2", userWrite("p1", 210, "t1"), "wzrost cudzego salda na podstawie deklaracji już rozliczonego depozytu")

        assertEquals(130, w.balance("p1"))
        assertCoinsConserved(w, "stara deklaracja")
    }

    @Test
    fun `wizyta z depozytem nie wraca do FREE, wiec depozyt nie zostaje bez wlasciciela`() = runTest {
        val w = world(slot("t1", "p1", 80))
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")

        rejected(
            w,
            "p1",
            listOf(SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, "t1", mapOf("status" to "FREE", "patientId" to null))),
            "zwolnienie terminu z otwartym depozytem"
        )

        assertEquals("BOOKED", w.appointment("t1").status)
        assertEquals(EscrowModel.HELD, w.escrow("t1")!!.status)
    }

    private suspend fun lateCancelByPatient(w: EscrowTestWorld, millisBeforeStart: Long) {
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.now = START + 3 * DAY - millisBeforeStart
    }

    private val cancelByPatient =
        SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, "t1", mapOf("status" to "CANCELLED", "cancelledBy" to "k1"))

    @Test
    fun `reguly - dokladnie 24 h przed terminem pacjent nie wymusi zwrotu surowa partia, wyplata przechodzi`() = runTest {
        val refund = listOf(cancelByPatient, escrowStatus("t1", EscrowModel.REFUNDED)) + userWrite("k1", 200, "t1")
        val release = listOf(cancelByPatient, escrowStatus("t1", EscrowModel.RELEASED)) + userWrite("p1", 130, "t1")

        val refundWorld = world(slot("t1", "p1", 80))
        lateCancelByPatient(refundWorld, DAY)
        rejected(refundWorld, "k1", refund, "zwrot pacjenta dokładnie 24 h przed terminem (reguła wymaga ostro więcej niż 24 h)")
        assertCoinsConserved(refundWorld, "zwrot na granicy 24 h")

        val releaseWorld = world(slot("t1", "p1", 80))
        lateCancelByPatient(releaseWorld, DAY)
        releaseWorld.attack("k1", release)
        assertEquals(EscrowModel.RELEASED, releaseWorld.escrow("t1")!!.status)
        assertCoinsConserved(releaseWorld, "wypłata na granicy 24 h")
    }

    @Test
    fun `reguly - 24 h i milisekunda przed terminem pacjent nie wyplaci psychologowi surowa partia, zwrot przechodzi`() = runTest {
        val refund = listOf(cancelByPatient, escrowStatus("t1", EscrowModel.REFUNDED)) + userWrite("k1", 200, "t1")
        val release = listOf(cancelByPatient, escrowStatus("t1", EscrowModel.RELEASED)) + userWrite("p1", 130, "t1")

        val releaseWorld = world(slot("t1", "p1", 80))
        lateCancelByPatient(releaseWorld, DAY + 1)
        rejected(releaseWorld, "k1", release, "wypłata pacjenta ponad 24 h przed terminem (okno wypłaty to najwyżej 24 h)")
        assertCoinsConserved(releaseWorld, "wypłata poza oknem 24 h")

        val refundWorld = world(slot("t1", "p1", 80))
        lateCancelByPatient(refundWorld, DAY + 1)
        refundWorld.attack("k1", refund)
        assertEquals(EscrowModel.REFUNDED, refundWorld.escrow("t1")!!.status)
        assertCoinsConserved(refundWorld, "zwrot poza oknem 24 h")
    }

    @Test
    fun `reguly - zwrot po oknie dokladnie 14 dni po terminie surowa partia odrzucona, milisekunde pozniej przechodzi`() = runTest {
        val window = listOf(escrowStatus("t1", EscrowModel.REFUNDED)) + userWrite("k1", 200, "t1")

        val onTheLine = world(slot("t1", "p1", 80))
        onTheLine.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        onTheLine.now = START + 3 * DAY + 14 * DAY
        rejected(onTheLine, "k1", window, "zwrot po oknie dokładnie 14 dni po terminie (reguła wymaga ostro więcej)")
        assertCoinsConserved(onTheLine, "zwrot na granicy 14 dni")

        val past = world(slot("t1", "p1", 80))
        past.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        past.now = START + 3 * DAY + 14 * DAY + 1
        past.attack("k1", window)
        assertEquals(EscrowModel.REFUNDED, past.escrow("t1")!!.status)
        assertCoinsConserved(past, "zwrot po upływie 14 dni")
    }

    private class Coverage {
        var attempts = 0
        var rejectedByClient = 0
        var rejectedByRules = 0
        var bookings = 0
        var refundsOnCancel = 0
        var payoutsOnCancel = 0
        var completions = 0
        var paidCompletions = 0
        var windowRefunds = 0

        fun add(other: Coverage) {
            attempts += other.attempts
            rejectedByClient += other.rejectedByClient
            rejectedByRules += other.rejectedByRules
            bookings += other.bookings
            refundsOnCancel += other.refundsOnCancel
            payoutsOnCancel += other.payoutsOnCancel
            completions += other.completions
            paidCompletions += other.paidCompletions
            windowRefunds += other.windowRefunds
        }

        override fun toString() =
            "attempts=$attempts rejectedByClient=$rejectedByClient rejectedByRules=$rejectedByRules bookings=$bookings " +
                "refundsOnCancel=$refundsOnCancel payoutsOnCancel=$payoutsOnCancel completions=$completions " +
                "paidCompletions=$paidCompletions windowRefunds=$windowRefunds"
    }

    private fun classify(w: EscrowTestWorld, coverage: Coverage, id: String, statusBefore: String, escrowBefore: String?) {
        val after = w.appointment(id)
        when {
            statusBefore == "FREE" && after.status == "BOOKED" -> coverage.bookings++
            statusBefore == "BOOKED" && after.status == "CANCELLED" && w.escrow(id)?.status == EscrowModel.REFUNDED -> coverage.refundsOnCancel++
            statusBefore == "BOOKED" && after.status == "CANCELLED" && w.escrow(id)?.status == EscrowModel.RELEASED -> coverage.payoutsOnCancel++
            statusBefore == "BOOKED" && after.status == "COMPLETED" -> {
                coverage.completions++
                if (escrowBefore == EscrowModel.HELD) coverage.paidCompletions++
            }
            statusBefore == "BOOKED" && after.status == "BOOKED" && escrowBefore == EscrowModel.HELD -> coverage.windowRefunds++
        }
    }

    private fun assertInvariants(w: EscrowTestWorld, expectedTotal: Int, label: String) {
        assertEquals("$label: suma sald i depozytów HELD musi być stała", expectedTotal, w.coinsInSystem())
        w.source.transactions.committedBatches.flatten()
            .filterIsInstance<SettlementWrite.Update>()
            .filter { it.collection.endsWith("/${FirestoreCollections.PRIVATE}") }
            .forEach { write ->
                assertTrue(
                    "$label: zapis salda ${write.collection} jest wartością bezwzględną ${write.fields["coinsBalance"]}, a ma być przyrostem",
                    write.fields["coinsBalance"] is BalanceDelta
                )
            }
        w.source.appointments.value.filter { it.status == "CANCELLED" }.forEach {
            val escrow = w.escrow(it.appointmentId)
            assertTrue(
                "$label: wizyta ${it.appointmentId} odwołana, a jej depozyt wciąż HELD",
                escrow == null || escrow.status != EscrowModel.HELD
            )
        }
    }

    private suspend fun runSeed(seed: Int, coverage: Coverage) {
        val random = Random(seed)
        val prices = listOf(80, 50, 60, 40, 0, 70)
        val slots = (0 until 20).map { index ->
            slot(
                id = "s$index",
                psychologistId = if (index % 2 == 0) "p1" else "p2",
                price = prices[index % prices.size],
                startsAt = START + (index + 1) * DAY + index * HOUR
            )
        }
        val w = EscrowTestWorld(patients = mapOf("k1" to 1000, "k2" to 1000), slots = slots)
        val expectedTotal = w.coinsInSystem()
        val patients = listOf("k1", "k2")
        val everyone = patients + listOf("p1", "p2")
        suspend fun scripted(id: String, label: String, operation: suspend () -> Unit) {
            val statusBefore = w.appointment(id).status
            val escrowBefore = w.escrow(id)?.status
            val commitsBefore = w.source.transactions.commits
            coverage.attempts++
            operation()
            check(w.source.transactions.commits > commitsBefore) { "krok skryptowy '$label' musi się powieść, bo gwarantuje pokrycie ścieżki" }
            classify(w, coverage, id, statusBefore, escrowBefore)
            assertInvariants(w, expectedTotal, "seed $seed krok skryptowy '$label'")
        }
        scripted("s0", "rezerwacja płatnego terminu psychologa p1, który zostanie zakończony") {
            w.source.bookAppointmentAtomically("s0", "k1", "p1", slots[0].priceCoins, "")
        }
        scripted("s1", "rezerwacja płatnego terminu psychologa p2, który nigdy nie zostanie zakończony") {
            w.source.bookAppointmentAtomically("s1", "k2", "p2", slots[1].priceCoins, "")
        }
        scripted("s2", "rezerwacja terminu odwołanego późno przez pacjenta") {
            w.source.bookAppointmentAtomically("s2", "k1", "p1", slots[2].priceCoins, "")
        }
        scripted("s3", "rezerwacja terminu odwołanego wcześnie przez pacjenta") {
            w.source.bookAppointmentAtomically("s3", "k2", "p2", slots[3].priceCoins, "")
        }
        scripted("s3", "wczesne odwołanie przez pacjenta: zwrot z depozytu") { w.source.cancelBooking("s3", "k2") }
        w.now = slots[2].startsAtMillis - 2 * HOUR
        scripted("s2", "późne odwołanie przez pacjenta: wypłata dla psychologa") { w.source.cancelBooking("s2", "k1") }
        repeat(300) { step ->
            val roll = random.nextInt(100)
            val all = w.source.appointments.value
            fun pick(allowAnyFallback: Boolean, preferred: (AppointmentModel) -> Boolean): String? {
                val candidates = all.filter(preferred)
                return when {
                    candidates.isNotEmpty() && (!allowAnyFallback || random.nextInt(100) < 85) -> candidates.random(random).appointmentId
                    allowAnyFallback -> all.random(random).appointmentId
                    else -> null
                }
            }
            val id: String? = when {
                roll < 38 -> pick(false) { it.status == "FREE" && it.startsAtMillis > w.now }
                roll < 50 -> {
                    val imminent = all.filter { it.status == "BOOKED" && it.psychologistId == "p1" && it.appointmentId != "s0" && it.startsAtMillis > w.now }
                        .minByOrNull { it.startsAtMillis }
                    if (imminent != null && random.nextInt(100) < 50) imminent.appointmentId else pick(false) { it.status == "BOOKED" && it.psychologistId == "p1" && it.appointmentId != "s0" }
                }
                roll < 66 -> pick(false) { it.status == "BOOKED" && it.startsAtMillis <= w.now && it.psychologistId == "p1" }
                roll < 76 -> pick(true) { it.status == "BOOKED" && w.now - it.startsAtMillis > 14 * DAY }
                else -> null
            }
            if (id == null) {
                w.now += listOf(HOUR, 6 * HOUR, DAY).random(random)
                return@repeat
            }
            val current = w.appointment(id)
            val parties = listOfNotNull(current.patientId, current.psychologistId)
            val commitsBefore = w.source.transactions.commits
            val statusBefore = current.status
            val escrowBefore = w.escrow(id)?.status
            try {
                coverage.attempts++
                when {
                    roll < 38 -> {
                        val buyer = if (random.nextInt(100) < 90) patients.random(random) else everyone.random(random)
                        w.source.bookAppointmentAtomically(id, buyer, current.psychologistId, current.priceCoins, "")
                    }
                    roll < 50 -> w.source.cancelBooking(id, if (random.nextInt(100) < 85) parties.random(random) else everyone.random(random))
                    roll < 66 -> w.source.completeAppointment(id)
                    else -> w.source.refundAbandonedBooking(id, current.patientId?.takeIf { random.nextInt(100) < 85 } ?: everyone.random(random))
                }
            } catch (e: UserMessageException) {
                coverage.rejectedByClient++
            } catch (e: IllegalStateException) {
                if (e.message?.startsWith("PERMISSION_DENIED") != true) throw e
                coverage.rejectedByRules++
            }
            if (w.source.transactions.commits > commitsBefore) classify(w, coverage, id, statusBefore, escrowBefore)
            assertInvariants(w, expectedTotal, "seed $seed krok $step")
        }
        w.now = maxOf(w.now, slots.maxOf { it.startsAtMillis }) + HOUR
        slots.filter { it.psychologistId == "p1" }.forEach { slot ->
            val current = w.appointment(slot.appointmentId)
            if (current.status != "BOOKED") return@forEach
            val escrowBefore = w.escrow(slot.appointmentId)?.status
            val commitsBefore = w.source.transactions.commits
            coverage.attempts++
            w.source.completeAppointment(slot.appointmentId)
            check(w.source.transactions.commits > commitsBefore) { "zakończenie rozpoczętej wizyty p1 musi się powieść" }
            classify(w, coverage, slot.appointmentId, current.status, escrowBefore)
            assertInvariants(w, expectedTotal, "seed $seed po zakończeniu: ${slot.appointmentId}")
        }
        w.now += 15 * DAY
        slots.filter { it.psychologistId == "p2" }.forEach { slot ->
            val current = w.appointment(slot.appointmentId)
            val patient = current.patientId ?: return@forEach
            val escrowBefore = w.escrow(slot.appointmentId)?.status
            val commitsBefore = w.source.transactions.commits
            try {
                coverage.attempts++
                w.source.refundAbandonedBooking(slot.appointmentId, patient)
            } catch (e: UserMessageException) {
                coverage.rejectedByClient++
            }
            if (w.source.transactions.commits > commitsBefore) classify(w, coverage, slot.appointmentId, current.status, escrowBefore)
            assertInvariants(w, expectedTotal, "seed $seed po upływie okna: ${slot.appointmentId}")
        }
    }

    private suspend fun assertSeeds(seeds: IntRange) {
        val sum = Coverage()
        seeds.forEach { seed ->
            val perSeed = Coverage()
            runSeed(seed, perSeed)
            println("COVERAGE seed=$seed $perSeed")
            assertTrue("seed $seed: za mało rezerwacji (${perSeed.bookings}): asercja sumy byłaby trywialna", perSeed.bookings >= MIN_BOOKINGS_PER_SEED)
            assertTrue("seed $seed: za mało zwrotów przy odwołaniu (${perSeed.refundsOnCancel})", perSeed.refundsOnCancel >= MIN_REFUNDS_PER_SEED)
            assertTrue("seed $seed: brak płatnego zakończenia (${perSeed.paidCompletions})", perSeed.paidCompletions >= MIN_PAID_COMPLETIONS_PER_SEED)
            assertTrue(
                "seed $seed: brak zwrotu po oknie 14 dni (${perSeed.windowRefunds}): ścieżka nie jest pokryta",
                perSeed.windowRefunds >= MIN_WINDOW_REFUNDS_PER_SEED
            )
            assertTrue("seed $seed: brak wypłaty przy późnym odwołaniu (${perSeed.payoutsOnCancel})", perSeed.payoutsOnCancel >= MIN_PAYOUTS_PER_SEED)
            sum.add(perSeed)
        }
        println("COVERAGE seeds=$seeds $sum")
    }

    @Test
    fun `losowe ciagi operacji klienta zachowuja sume i pokrywaja kazda sciezke - ziarna 1 do 8`() = runTest { assertSeeds(1..8) }

    @Test
    fun `losowe ciagi operacji klienta zachowuja sume i pokrywaja kazda sciezke - ziarna 9 do 16`() = runTest { assertSeeds(9..16) }

    private companion object {
        const val MIN_BOOKINGS_PER_SEED = 8
        const val MIN_REFUNDS_PER_SEED = 1
        const val MIN_PAID_COMPLETIONS_PER_SEED = 1
        const val MIN_WINDOW_REFUNDS_PER_SEED = 1
        const val MIN_PAYOUTS_PER_SEED = 1
    }
}
