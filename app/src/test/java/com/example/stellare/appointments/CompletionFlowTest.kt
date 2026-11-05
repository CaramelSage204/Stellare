package com.example.stellare.appointments

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.CompletionModel
import com.example.stellare.data.model.CompletionOutcome
import com.example.stellare.data.remote.CompletionFlow
import com.example.stellare.data.remote.CompletionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CompletionFlowTest {

    private class RulesLikeStore(
        val markers: MutableSet<String> = mutableSetOf(),
        var batchFailure: Exception? = null,
        var markerAppearsDuringBatch: Boolean = false
    ) : CompletionStore {
        val statusUpdates = mutableListOf<String>()
        val batches = mutableListOf<Triple<String, String, CompletionModel>>()
        var batchAttempts = 0
        var existsChecks = 0

        override suspend fun markerExists(markerId: String): Boolean {
            existsChecks++
            return markerId in markers
        }

        override suspend fun completeWithMarker(appointmentId: String, markerId: String, marker: CompletionModel): CompletionOutcome {
            batchAttempts++
            batchFailure?.let { throw it }
            if (markerAppearsDuringBatch) {
                markers += markerId
                throw IllegalStateException("PERMISSION_DENIED")
            }
            if (markerId in markers) throw IllegalStateException("PERMISSION_DENIED")
            markers += markerId
            batches += Triple(appointmentId, markerId, marker)
            statusUpdates += appointmentId
            return CompletionOutcome.NoCoinsMoved
        }

        override suspend fun completeOnly(appointmentId: String): CompletionOutcome {
            statusUpdates += appointmentId
            return CompletionOutcome.NoCoinsMoved
        }
    }

    private val wizyta = AppointmentModel(
        appointmentId = "w1", psychologistId = "p1", patientId = "k1",
        date = "2026-10-01", time = "10:00", status = "BOOKED", priceCoins = 80
    )

    @Test
    fun `pierwsza wizyta pary zmienia status i tworzy znacznik w jednej partii`() = runTest {
        val store = RulesLikeStore()
        CompletionFlow(store) { 123L }.complete(wizyta)

        assertEquals(listOf("w1"), store.statusUpdates)
        val (appointmentId, markerId, marker) = store.batches.single()
        assertEquals("w1", appointmentId)
        assertEquals("p1_k1", markerId)
        assertEquals(CompletionModel("p1", "k1", "w1", 123L), marker)
    }

    @Test
    fun `druga wizyta tej samej pary zmienia status bez proby tworzenia znacznika`() = runTest {
        val store = RulesLikeStore(markers = mutableSetOf("p1_k1"))
        CompletionFlow(store) { 1L }.complete(wizyta.copy(appointmentId = "w2"))

        assertEquals(listOf("w2"), store.statusUpdates)
        assertEquals(0, store.batchAttempts)
    }

    @Test
    fun `znacznik utworzony miedzy sprawdzeniem a partia nie konczy sie bledem`() = runTest {
        val store = RulesLikeStore(markerAppearsDuringBatch = true)
        CompletionFlow(store) { 1L }.complete(wizyta)

        assertEquals(1, store.batchAttempts)
        assertEquals(listOf("w1"), store.statusUpdates)
    }

    @Test
    fun `awaria partii bez znacznika jest zglaszana dalej`() = runTest {
        val store = RulesLikeStore(batchFailure = IllegalStateException("brak sieci"))
        try {
            CompletionFlow(store) { 1L }.complete(wizyta)
            fail("oczekiwano wyjatku")
        } catch (e: IllegalStateException) {
            assertEquals("brak sieci", e.message)
        }
        assertTrue(store.statusUpdates.isEmpty())
    }

    @Test
    fun `anulowanie korutyny nie jest traktowane jako odmowa znacznika`() = runTest {
        val store = RulesLikeStore(batchFailure = CancellationException("anulowano"))
        try {
            CompletionFlow(store) { 1L }.complete(wizyta)
            fail("oczekiwano wyjatku")
        } catch (e: CancellationException) {
            assertEquals("anulowano", e.message)
        }
        assertEquals(1, store.existsChecks)
        assertTrue(store.statusUpdates.isEmpty())
    }

    @Test
    fun `wizyta bez pacjenta jest odrzucona przed jakimkolwiek zapisem`() = runTest {
        val store = RulesLikeStore()
        try {
            CompletionFlow(store) { 1L }.complete(wizyta.copy(patientId = null))
            fail("oczekiwano wyjatku")
        } catch (e: IllegalStateException) {
            assertEquals("Wizyta nie ma przypisanego pacjenta.", e.message)
        }
        assertEquals(0, store.existsChecks)
        assertTrue(store.statusUpdates.isEmpty())
    }

    @Test
    fun `identyfikator znacznika sklada sie z psychologa i pacjenta w tej kolejnosci`() {
        assertEquals("p1_k1", CompletionModel.idFor("p1", "k1"))
    }
}
