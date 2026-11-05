package com.example.stellare.appointments

import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.remote.BalanceDelta
import com.example.stellare.data.remote.SettlementWrite
import com.example.stellare.data.remote.WriteSink
import com.example.stellare.data.remote.applyWrites
import com.example.stellare.data.remote.firestoreCreateData
import com.example.stellare.data.remote.firestoreFields
import com.example.stellare.data.remote.firestoreValue
import com.example.stellare.fakes.EscrowTestWorld
import com.example.stellare.fakes.EscrowTestWorld.Companion.DAY
import com.example.stellare.fakes.EscrowTestWorld.Companion.HOUR
import com.example.stellare.fakes.EscrowTestWorld.Companion.START
import com.example.stellare.fakes.EscrowTestWorld.Companion.slot
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettlementWriteConversionTest {

    private fun operandOf(value: Any?): Any? {
        val field = value!!.javaClass.getDeclaredField("operand")
        field.isAccessible = true
        return field.get(value)
    }

    @Test
    fun `przyrost salda trafia do Firestore jako FieldValue increment o dokladnie ta kwote`() {
        listOf(80, -80, 1000).forEach { amount ->
            val converted = firestoreValue(BalanceDelta(amount))

            assertTrue(
                "przyrost $amount nie stał się FieldValue.increment, tylko ${converted?.javaClass}: serwer dostałby surową liczbę " +
                    "i każde rozliczenie dostałoby odmowę, a monety zostałyby w HELD",
                converted is FieldValue
            )
            assertEquals("kwota przyrostu zmieniona w konwersji", amount.toLong(), operandOf(converted))
            assertTrue(converted!!.javaClass.simpleName.contains("NumericIncrement"))
        }
    }

    @Test
    fun `pola niebedace przyrostem przechodza bez zmian`() {
        assertSame("t1", firestoreValue("t1"))
        assertEquals(null, firestoreValue(null))
        assertEquals(120, firestoreValue(120))
    }

    @Test
    fun `konwersja pol zamienia tylko przyrost i zostawia deklaracje`() {
        val converted = firestoreFields(mapOf("coinsBalance" to BalanceDelta(80), "escrowSettlement" to "t1"))

        assertEquals("t1", converted["escrowSettlement"])
        assertTrue(converted["coinsBalance"] is FieldValue)
        assertEquals(80L, operandOf(converted["coinsBalance"]))
    }

    @Test
    fun `tworzenie dokumentu przyjmuje model, ale odrzuca mape z przyrostem i sam przyrost`() {
        val escrow = EscrowModel("t1", "k1", "p1", 80)
        assertSame(escrow, firestoreCreateData(escrow))

        listOf<Any>(mapOf("coinsBalance" to BalanceDelta(80)), BalanceDelta(80)).forEach { raw ->
            try {
                firestoreCreateData(raw)
                fail("surowa mapa lub przyrost przeszły do transaction.set bez konwersji: $raw")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!, e.message!!.contains("zniszczyłby monety"))
            }
        }
    }

    private class RecordingSink : WriteSink {
        val sets = mutableListOf<Triple<String, String, Any>>()
        val updates = mutableListOf<Triple<String, String, Map<String, Any?>>>()
        override fun set(collection: String, id: String, data: Any) { sets += Triple(collection, id, data) }
        override fun update(collection: String, id: String, fields: Map<String, Any?>) { updates += Triple(collection, id, fields) }
    }

    @Test
    fun `partia przechodzi do Firestore przez konwersje - przyrost jako increment, model bez zmian`() {
        val sink = RecordingSink()
        val escrow = EscrowModel("t1", "k1", "p1", 80)

        applyWrites(
            listOf(
                SettlementWrite.Create("escrows", "t1", escrow),
                SettlementWrite.Update("users/k1/private", "contact", mapOf("coinsBalance" to BalanceDelta(-80), "escrowSettlement" to "t1"))
            ),
            sink
        )

        assertEquals(listOf(Triple("escrows", "t1", escrow as Any)), sink.sets)
        val fields = sink.updates.single().third
        assertTrue(
            "zapis salda dotarł do Firestore jako ${fields["coinsBalance"]?.javaClass}, a nie jako increment: odmowa przy każdym rozliczeniu, monety w HELD",
            fields["coinsBalance"] is FieldValue
        )
        assertEquals(-80L, operandOf(fields["coinsBalance"]))
        assertEquals("t1", fields["escrowSettlement"])
    }

    @Test
    fun `zadne tworzenie dokumentu w produkcyjnych partiach nie niesie przyrostu ani mapy`() = runTest {
        val w = EscrowTestWorld(slots = listOf(slot("t1", "p1", 80), slot("t2", "p1", 60, hour = 11)))
        w.source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        w.source.bookAppointmentAtomically("t2", "k2", "p1", 60, "")
        w.source.cancelBooking("t1", "p1")
        w.now = START + 3 * DAY + HOUR
        w.source.completeAppointment("t2")

        val creates = w.source.transactions.committedBatches.flatten().filterIsInstance<SettlementWrite.Create>()
        assertTrue("za mało zapisów tworzących, by asercja coś znaczyła: ${creates.size}", creates.size >= 5)
        creates.forEach { create ->
            assertFalse("tworzenie ${create.collection}/${create.id} niesie surową mapę lub przyrost", create.data is Map<*, *> || create.data is BalanceDelta)
            firestoreCreateData(create.data)
        }
    }
}
