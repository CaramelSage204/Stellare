package com.example.stellare.ui

import com.example.stellare.data.model.UserMessageException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WriteReconciliationTest {

    private val failure = "Nie udało się wykonać operacji."
    private val uncertain = "Nie wiemy, czy operacja została zapisana."

    private fun firestore(code: Code, cause: Throwable? = null) =
        FirebaseFirestoreException("test", code).also { e -> cause?.let { e.initCause(it) } }

    private class Probe<T : Any>(private val answer: () -> T?) {
        var calls = 0
            private set

        suspend fun check(): T? {
            calls++
            return answer()
        }
    }

    private suspend fun <T : Any> reconcile(error: Exception, probe: Probe<T>): WriteOutcome<T> =
        WriteReconciliation.outcome(error, failure, uncertain) { probe.check() }

    @Test
    fun `zapis rzuca UNAVAILABLE a sprawdzenie pokazuje ze doszedl - sukces bez bledu dla uzytkownika`() = runTest {
        val probe = Probe { "wpis" }

        val outcome = reconcile(firestore(Code.UNAVAILABLE), probe)

        assertEquals(WriteOutcome.Succeeded("wpis"), outcome)
        assertEquals(1, probe.calls)
    }

    @Test
    fun `kazdy kod przejsciowy po ktorym zapis mogl dojsc prowadzi do sprawdzenia i do sukcesu`() = runTest {
        listOf(Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED, Code.ABORTED, Code.UNKNOWN, Code.INTERNAL, Code.CANCELLED).forEach { code ->
            val probe = Probe { "wpis" }

            val outcome = reconcile(firestore(code), probe)

            assertEquals("kod $code: zapis mógł dojść, więc użytkownik nie może zobaczyć porażki", WriteOutcome.Succeeded("wpis"), outcome)
            assertEquals("kod $code: sprawdzenie musi się odbyć", 1, probe.calls)
        }
    }

    @Test
    fun `zapis rzuca wyjatek a sprawdzenie pokazuje ze nie doszedl - porazka z komunikatem`() = runTest {
        val probe = Probe<String> { null }

        val outcome = reconcile(firestore(Code.UNAVAILABLE), probe)

        assertEquals(WriteOutcome.Failed(UserFacingError.NO_CONNECTION), outcome)
        assertEquals(1, probe.calls)
    }

    @Test
    fun `wyjatek spoza Firebase bez dowodu w bazie daje komunikat zastepczy`() = runTest {
        val probe = Probe<String> { null }

        val outcome = reconcile(IllegalStateException("cokolwiek"), probe)

        assertEquals(WriteOutcome.Failed(failure), outcome)
        assertEquals(1, probe.calls)
    }

    @Test
    fun `odmowa regul nie uruchamia sprawdzenia - nie zostawia stanu`() = runTest {
        listOf(Code.PERMISSION_DENIED, Code.INVALID_ARGUMENT, Code.UNIMPLEMENTED).forEach { code ->
            val probe = Probe { "wpis" }

            val outcome = reconcile(firestore(code), probe)

            assertEquals("kod $code", WriteOutcome.Failed(failure), outcome)
            assertEquals("kod $code: błąd trwały nie zostawia stanu, więc odczyt byłby zbędny", 0, probe.calls)
        }
    }

    @Test
    fun `odmowa zagniezdzona w innym wyjatku tez jest trwala`() = runTest {
        val probe = Probe { "wpis" }

        val outcome = reconcile(IllegalStateException("opakowanie", firestore(Code.PERMISSION_DENIED)), probe)

        assertEquals(WriteOutcome.Failed(failure), outcome)
        assertEquals(0, probe.calls)
    }

    @Test
    fun `blad domenowy zachowuje komunikat i nie uruchamia sprawdzenia`() = runTest {
        val probe = Probe { "wpis" }

        val outcome = reconcile(UserMessageException("Za mało monet w portfelu."), probe)

        assertEquals(WriteOutcome.Failed("Za mało monet w portfelu."), outcome)
        assertEquals(0, probe.calls)
    }

    @Test
    fun `gdy samo sprawdzenie tez zawodzi wynik to niewiadoma, nigdy porazka ani sukces`() = runTest {
        listOf(Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED, Code.ABORTED).forEach { code ->
            val probe = Probe<String> { throw firestore(Code.UNAVAILABLE) }

            val outcome = reconcile(firestore(code), probe)

            assertEquals("kod $code", WriteOutcome.Unknown(uncertain), outcome)
            assertEquals(1, probe.calls)
        }
    }

    @Test
    fun `niewiadoma niesie komunikat o niepewnosci, a nie komunikat porazki`() = runTest {
        val outcome = reconcile(firestore(Code.UNAVAILABLE), Probe<String> { throw IllegalStateException("brak sieci") })

        val message = (outcome as WriteOutcome.Unknown).message
        assertEquals(uncertain, message)
        assertFalse(message == failure)
    }

    @Test
    fun `anulowanie podczas sprawdzenia jest przekazywane dalej, nie zamieniane w niewiadoma`() = runTest {
        try {
            reconcile(firestore(Code.UNAVAILABLE), Probe<String> { throw CancellationException("anulowano") })
            fail("anulowanie korutyny musi przejść przez uzgadnianie")
        } catch (expected: CancellationException) {
            assertEquals("anulowano", expected.message)
        }
    }

    @Test
    fun `anulowanie zapisu jest przekazywane dalej bez sprawdzenia`() = runTest {
        val probe = Probe { "wpis" }
        val logged = mutableListOf<Exception>()
        try {
            WriteReconciliation.attempt(
                write = { throw CancellationException("anulowano") },
                landed = { probe.check() },
                failureMessage = failure,
                uncertainMessage = uncertain,
                onError = { logged += it }
            )
            fail("anulowanie korutyny musi przejść przez uzgadnianie")
        } catch (expected: CancellationException) {
            assertEquals(0, probe.calls)
            assertTrue("anulowanie nie jest błędem zapisu i nie trafia do dziennika błędów", logged.isEmpty())
        }
    }

    @Test
    fun `udany zapis nie wywoluje sprawdzenia`() = runTest {
        val probe = Probe { "inny" }

        val outcome = WriteReconciliation.attempt(
            write = { "wpis" },
            landed = { probe.check() },
            failureMessage = failure,
            uncertainMessage = uncertain
        )

        assertEquals(WriteOutcome.Succeeded("wpis"), outcome)
        assertEquals(0, probe.calls)
    }

    @Test
    fun `blad zapisu trafia do dziennika raz`() = runTest {
        val seen = mutableListOf<Exception>()
        val error = firestore(Code.UNAVAILABLE)

        WriteReconciliation.attempt(
            write = { throw error },
            landed = { null as String? },
            failureMessage = failure,
            uncertainMessage = uncertain,
            onError = { seen += it }
        )

        assertEquals(listOf<Exception>(error), seen)
    }

    @Test
    fun `ponowienie ze sprawdzeniem wstepnym nie pisze drugi raz, gdy wpis juz jest`() = runTest {
        var writes = 0

        val outcome = WriteReconciliation.attempt(
            write = { writes++; "nowy" },
            landed = { "stary" },
            failureMessage = failure,
            uncertainMessage = uncertain,
            verifyFirst = true
        )

        assertEquals(WriteOutcome.Succeeded("stary"), outcome)
        assertEquals("zapis nie może się powtórzyć, gdy sprawdzenie wstępne znalazło wpis", 0, writes)
    }

    @Test
    fun `ponowienie ze sprawdzeniem wstepnym pisze, gdy wpisu nie ma`() = runTest {
        var writes = 0

        val outcome = WriteReconciliation.attempt(
            write = { writes++; "nowy" },
            landed = { null },
            failureMessage = failure,
            uncertainMessage = uncertain,
            verifyFirst = true
        )

        assertEquals(WriteOutcome.Succeeded("nowy"), outcome)
        assertEquals(1, writes)
    }

    @Test
    fun `ponowienie ze sprawdzeniem wstepnym, ktore zawodzi, nie pisze w ciemno`() = runTest {
        var writes = 0

        val outcome = WriteReconciliation.attempt(
            write = { writes++; "nowy" },
            landed = { throw firestore(Code.UNAVAILABLE) },
            failureMessage = failure,
            uncertainMessage = uncertain,
            verifyFirst = true
        )

        assertEquals(WriteOutcome.Unknown(uncertain), outcome)
        assertEquals(0, writes)
    }

    @Test
    fun `bez sprawdzenia wstepnego pierwsza proba nie czyta niczego przed zapisem`() = runTest {
        val probe = Probe { "wpis" }

        WriteReconciliation.attempt(
            write = { "nowy" },
            landed = { probe.check() },
            failureMessage = failure,
            uncertainMessage = uncertain
        )

        assertEquals(0, probe.calls)
    }

    @Test
    fun `komunikaty o niepewnosci mowia czego nie wiemy i nie twierdza porazki ani nie obiecuja`() {
        val all = listOf(
            UncertainWriteMessages.BOOKING,
            UncertainWriteMessages.COMPLETION,
            UncertainWriteMessages.CANCELLATION,
            UncertainWriteMessages.RECLAIM,
            UncertainWriteMessages.TOP_UP
        )
        val forbidden = listOf("nie udało", "spróbuj", "za chwilę", "po chwili", "wrócą", "zostanie", "zostaną", "niebawem")

        assertEquals(5, all.toSet().size)
        all.forEach { message ->
            assertTrue("komunikat musi zaczynać się od \"Nie wiemy\": $message", message.startsWith("Nie wiemy"))
            forbidden.forEach { phrase ->
                assertFalse("komunikat o niepewności nie może zawierać \"$phrase\": $message", message.lowercase().contains(phrase))
            }
        }
    }
}
