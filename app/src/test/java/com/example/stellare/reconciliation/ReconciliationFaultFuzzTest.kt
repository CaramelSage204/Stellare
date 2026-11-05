package com.example.stellare.reconciliation

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.fakes.EscrowTestWorld
import com.example.stellare.fakes.EscrowTestWorld.Companion.DAY
import com.example.stellare.fakes.EscrowTestWorld.Companion.HOUR
import com.example.stellare.fakes.EscrowTestWorld.Companion.START
import com.example.stellare.fakes.EscrowTestWorld.Companion.slot
import com.example.stellare.ui.WriteOutcome
import com.example.stellare.ui.WriteReconciliation
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.random.Random

class ReconciliationFaultFuzzTest {

    private enum class Kind { BOOK, CANCEL, COMPLETE, RECLAIM }

    private enum class Fault { NONE, LOST_BEFORE_COMMIT, RESPONSE_LOST, RESPONSE_LOST_AND_READS_DOWN, WRITE_AND_READS_DOWN, RULES_DENIAL }

    private data class Op(val kind: Kind, val appointmentId: String, val actor: String, val mustFail: Boolean)

    private class Stats {
        val landed = mutableMapOf<Kind, Int>()
        var lostResponseSucceeded = 0
        var failedBeforeCommit = 0
        var unknownLanded = 0
        var unknownNotLanded = 0
        var rulesDenialFailed = 0
        var domainFailedUnderBrokenReads = 0
        var retriesChecked = 0
        var steps = 0
        var skipped = 0

        override fun toString() = "kroki=$steps pominięte=$skipped wylądowały=$landed zgubiona-odpowiedź-sukces=$lostResponseSucceeded " +
            "przed-zatwierdzeniem-porażka=$failedBeforeCommit niewiadoma-wylądowała=$unknownLanded niewiadoma-nie-wylądowała=$unknownNotLanded " +
            "odmowa-regul=$rulesDenialFailed domenowa-przy-zepsutych-odczytach=$domainFailedUnderBrokenReads ponowienia=$retriesChecked"
    }

    private fun unavailable() = FirebaseFirestoreException("brak sieci", Code.UNAVAILABLE)
    private fun denied() = FirebaseFirestoreException("odmowa regul", Code.PERMISSION_DENIED)

    private fun freshWorld(): EscrowTestWorld {
        val slots = (0 until SLOT_COUNT).map { i ->
            val psychologist = if (i % 2 == 0) "p1" else "p2"
            slot("t$i", psychologist, if (i % 3 == 0) 20 else 80, START + (1 + i) * DAY, hour = 8 + i % 10)
        }
        return EscrowTestWorld(
            patients = mapOf("k1" to 3000, "k2" to 3000),
            psychologists = mapOf("p1" to 50, "p2" to 50),
            slots = slots
        )
    }

    private fun fingerprint(w: EscrowTestWorld): List<Any?> = listOf(
        w.source.appointments.value.sortedBy { it.appointmentId }.map { Triple(it.status, it.patientId, it.cancelledBy) },
        w.source.escrows.value.toSortedMap().map { it.key to it.value.status },
        listOf("k1", "k2", "p1", "p2").map { w.balance(it) },
        w.wallet.transactions.value.map { it.transactionId }.sorted(),
        w.source.completions.value.keys.sorted()
    )

    private suspend fun EscrowTestWorld.perform(op: Op): WriteOutcome<out Any> = when (op.kind) {
        Kind.BOOK -> WriteReconciliation.attempt(
            write = {
                val slot = appointment(op.appointmentId)
                source.bookAppointmentAtomically(op.appointmentId, op.actor, slot.psychologistId, slot.priceCoins, "")
            },
            landed = { Unit.takeIf { source.bookingLanded(op.appointmentId, op.actor) } },
            failureMessage = "porażka",
            uncertainMessage = "niewiadoma"
        )
        Kind.CANCEL -> WriteReconciliation.attempt(
            write = { source.cancelBooking(op.appointmentId, op.actor) },
            landed = { source.cancellationLanded(op.appointmentId, op.actor) },
            failureMessage = "porażka",
            uncertainMessage = "niewiadoma"
        )
        Kind.COMPLETE -> WriteReconciliation.attempt(
            write = { source.completeAppointment(op.appointmentId) },
            landed = { source.completionLanded(op.appointmentId) },
            failureMessage = "porażka",
            uncertainMessage = "niewiadoma"
        )
        Kind.RECLAIM -> WriteReconciliation.attempt(
            write = { source.refundAbandonedBooking(op.appointmentId, op.actor) },
            landed = { source.reclaimLanded(op.appointmentId) },
            failureMessage = "porażka",
            uncertainMessage = "niewiadoma"
        )
    }

    private fun EscrowTestWorld.armFault(fault: Fault) {
        val runner = source.transactions
        when (fault) {
            Fault.NONE -> Unit
            Fault.LOST_BEFORE_COMMIT -> runner.failBeforeCommit = unavailable()
            Fault.RESPONSE_LOST -> runner.loseResponseAfterCommit = unavailable()
            Fault.RESPONSE_LOST_AND_READS_DOWN -> {
                runner.loseResponseAfterCommit = unavailable()
                runner.readFailureAfterLostResponse = unavailable()
            }
            Fault.WRITE_AND_READS_DOWN -> {
                runner.failBeforeCommit = unavailable()
                runner.readOnlyFailure = unavailable()
            }
            Fault.RULES_DENIAL -> runner.failBeforeCommit = denied()
        }
    }

    private fun EscrowTestWorld.disarmFaults() {
        val runner = source.transactions
        runner.failBeforeCommit = null
        runner.loseResponseAfterCommit = null
        runner.readOnlyFailure = null
        runner.readFailureAfterLostResponse = null
    }

    private fun EscrowTestWorld.chooseOp(random: Random): Op? {
        val all = source.appointments.value
        fun pick(list: List<AppointmentModel>) = list.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }
        val abandonedAfter = 14 * DAY
        val weighted = listOf<Pair<Double, () -> Op?>>(
            40.0 to {
                pick(all.filter { it.status == "FREE" && it.startsAtMillis > now })
                    ?.let { Op(Kind.BOOK, it.appointmentId, if (random.nextBoolean()) "k1" else "k2", mustFail = false) }
            },
            10.0 to {
                pick(all.filter { it.status == "BOOKED" && it.startsAtMillis > now })
                    ?.let { Op(Kind.CANCEL, it.appointmentId, if (random.nextBoolean()) checkNotNull(it.patientId) else it.psychologistId, mustFail = false) }
            },
            30.0 to {
                pick(all.filter { it.status == "BOOKED" && it.startsAtMillis < now && it.startsAtMillis > now - 3 * DAY })
                    ?.let { Op(Kind.COMPLETE, it.appointmentId, it.psychologistId, mustFail = false) }
            },
            20.0 to {
                pick(all.filter { it.status == "BOOKED" && it.startsAtMillis < now - abandonedAfter && escrow(it.appointmentId)?.status == EscrowModel.HELD })
                    ?.let { Op(Kind.RECLAIM, it.appointmentId, checkNotNull(it.patientId), mustFail = false) }
            }
        )
        val invalid = listOf<() -> Op?>(
            {
                pick(all.filter { it.status == "BOOKED" })
                    ?.let { Op(Kind.BOOK, it.appointmentId, if (it.patientId == "k1") "k2" else "k1", mustFail = true) }
            },
            {
                pick(all.filter { it.status == "FREE" })
                    ?.let { Op(Kind.COMPLETE, it.appointmentId, it.psychologistId, mustFail = true) }
            },
            {
                pick(all.filter { it.status == "BOOKED" })
                    ?.let { Op(Kind.RECLAIM, it.appointmentId, it.psychologistId, mustFail = true) }
            }
        )
        val candidates = weighted.sortedByDescending { (weight, _) -> random.nextDouble().pow(1.0 / weight) }.map { it.second }
        val pool = if (random.nextInt(100) < 12) invalid.shuffled(random) else candidates
        return pool.firstNotNullOfOrNull { it() }
    }

    private fun faultFor(random: Random): Fault {
        val roll = random.nextInt(100)
        return when {
            roll < 22 -> Fault.NONE
            roll < 40 -> Fault.LOST_BEFORE_COMMIT
            roll < 62 -> Fault.RESPONSE_LOST
            roll < 78 -> Fault.RESPONSE_LOST_AND_READS_DOWN
            roll < 90 -> Fault.WRITE_AND_READS_DOWN
            else -> Fault.RULES_DENIAL
        }
    }

    private suspend fun run(seed: Int, stats: Stats) {
        val w = freshWorld()
        val random = Random(seed)
        val totalCoins = w.coinsInSystem()

        val probeBefore = fingerprint(w)
        w.perform(Op(Kind.BOOK, "t0", "k1", mustFail = false))
        assertNotEquals("przyrząd musi widzieć zmianę po udanej rezerwacji, inaczej każda asercja jest trywialna", probeBefore, fingerprint(w))
        assertEquals(totalCoins, w.coinsInSystem())
        w.source.transactions.committedBatches.size.let { assertTrue("próbna rezerwacja musiała zatwierdzić partię", it >= 1) }

        repeat(STEPS) {
            w.now += random.nextLong(0, 10 * HOUR)
            val op = w.chooseOp(random)
            if (op == null) {
                stats.skipped++
                return@repeat
            }
            val fault = faultFor(random)
            val before = fingerprint(w)
            w.armFault(fault)
            val outcome = w.perform(op)
            val failureConsumed = w.source.transactions.failBeforeCommit == null
            w.disarmFaults()
            val after = fingerprint(w)
            val changed = before != after
            stats.steps++
            val context = "seed=$seed krok=${stats.steps} op=$op usterka=$fault wynik=$outcome"

            assertEquals("suma monet musi być stała: $context", totalCoins, w.coinsInSystem())
            when (outcome) {
                is WriteOutcome.Succeeded -> assertTrue("sukces bez zmiany stanu to fałszywy sukces: $context", changed)
                is WriteOutcome.Failed -> assertTrue("porażka przy zmienionym stanie to fałszywa porażka: $context", !changed)
                is WriteOutcome.Unknown -> assertTrue(
                    "niewiadoma wolno tylko, gdy sprawdzenie nie mogło się udać: $context",
                    fault == Fault.RESPONSE_LOST_AND_READS_DOWN || fault == Fault.WRITE_AND_READS_DOWN
                )
            }
            if (op.mustFail) {
                assertTrue("operacja niedozwolona nie może zmienić stanu: $context", !changed)
                assertTrue("operacja niedozwolona nie może dać sukcesu: $context", outcome !is WriteOutcome.Succeeded)
                if (fault == Fault.WRITE_AND_READS_DOWN && outcome is WriteOutcome.Failed) stats.domainFailedUnderBrokenReads++
            }
            if (fault == Fault.RULES_DENIAL) {
                assertTrue("odmowa reguł jest porażką bez śladu: $context", outcome is WriteOutcome.Failed && !changed)
                stats.rulesDenialFailed++
            }
            if (fault == Fault.RESPONSE_LOST && changed) {
                assertTrue("zapis doszedł, a użytkownik zobaczył błąd: $context", outcome is WriteOutcome.Succeeded)
                stats.lostResponseSucceeded++
            }
            if (fault == Fault.LOST_BEFORE_COMMIT && !op.mustFail && failureConsumed && !changed) {
                assertTrue("zapis nie doszedł, a użytkownik nie zobaczył porażki: $context", outcome is WriteOutcome.Failed)
                stats.failedBeforeCommit++
            }
            if (outcome is WriteOutcome.Unknown) if (changed) stats.unknownLanded++ else stats.unknownNotLanded++
            if (changed) {
                stats.landed.merge(op.kind, 1, Int::plus)
                val settled = fingerprint(w)
                w.perform(op)
                assertEquals("ponowienie operacji, która doszła, nie może nic zmienić: $context", settled, fingerprint(w))
                assertEquals(totalCoins, w.coinsInSystem())
                stats.retriesChecked++
            }
        }
    }

    @Test
    fun `losowe usterki sieci - wynik zawsze zgadza sie ze stanem bazy, monety sie nie dubluja, a ponowienie jest nieszkodliwe`() = runTest {
        val stats = Stats()

        SEEDS.forEach { seed -> run(seed, stats) }

        val summary = stats.toString()
        Kind.values().forEach { kind ->
            assertTrue("test losowy trywialny: za mało zatwierdzonych $kind (${stats.landed[kind] ?: 0}) — $summary", (stats.landed[kind] ?: 0) >= MIN_PER_KIND)
        }
        assertTrue("za mało sukcesów po utraconej odpowiedzi: $summary", stats.lostResponseSucceeded >= MIN_PER_OUTCOME)
        assertTrue("za mało porażek przed zatwierdzeniem: $summary", stats.failedBeforeCommit >= MIN_PER_OUTCOME)
        assertTrue("za mało niewiadomych z zapisem, który doszedł: $summary", stats.unknownLanded >= MIN_PER_OUTCOME)
        assertTrue("za mało niewiadomych z zapisem, który nie doszedł: $summary", stats.unknownNotLanded >= MIN_PER_OUTCOME)
        assertTrue("za mało odmów reguł: $summary", stats.rulesDenialFailed >= MIN_PER_OUTCOME)
        assertTrue("za mało błędów domenowych przy zepsutych odczytach: $summary", stats.domainFailedUnderBrokenReads >= MIN_DOMAIN)
        assertTrue("za mało sprawdzonych ponowień: $summary", stats.retriesChecked >= MIN_PER_OUTCOME)
        println("RECONCILIATION-FUZZ: $summary")
    }

    private companion object {
        const val SLOT_COUNT = 150
        const val STEPS = 450
        val SEEDS = listOf(11, 23, 37)
        const val MIN_PER_KIND = 12
        const val MIN_PER_OUTCOME = 12
        const val MIN_DOMAIN = 3
    }
}
