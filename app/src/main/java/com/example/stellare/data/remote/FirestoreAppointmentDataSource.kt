package com.example.stellare.data.remote

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.UserMessageException
import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.CompletionModel
import com.example.stellare.data.model.CompletionOutcome
import com.google.firebase.Firebase
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreAppointmentDataSource(
    private val clock: () -> Long = System::currentTimeMillis,
    runner: SettlementRunner = FirestoreSettlementRunner()
) : AppointmentDataSource {
    private val db by lazy { Firebase.firestore }
    private val collection by lazy { db.collection(FirestoreCollections.APPOINTMENTS) }
    private val escrow = EscrowTransactions(runner, clock)

    override suspend fun saveAppointment(appointment: AppointmentModel) {
        val id = if (appointment.appointmentId.isEmpty()) {
            collection.document().id
        } else {
            appointment.appointmentId
        }
        val appointmentToSave = appointment.copy(appointmentId = id)
        collection.document(id).set(appointmentToSave).await()
    }

    override fun getAppointmentsForPsychologist(psychologistId: String): Flow<List<AppointmentModel>> = callbackFlow {
        if (psychologistId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = collection
            .whereEqualTo("psychologistId", psychologistId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreAppointmentDS", "Error fetching appointments for psych $psychologistId: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                trySend(appointmentsOf(snap))
            }
        awaitClose { listener.remove() }
    }

    override fun getAppointmentsForPatient(patientId: String): Flow<List<AppointmentModel>> = callbackFlow {
        if (patientId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = collection
            .whereEqualTo("patientId", patientId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreAppointmentDS", "Error fetching appointments for patient $patientId: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                trySend(appointmentsOf(snap))
            }
        awaitClose { listener.remove() }
    }

    override fun getFreeSlotsForPsychologist(psychologistId: String): Flow<List<AppointmentModel>> = callbackFlow {
        if (psychologistId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = collection
            .whereEqualTo("psychologistId", psychologistId)
            .whereEqualTo("status", "FREE")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreAppointmentDS", "Error fetching free slots for psych $psychologistId: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                trySend(appointmentsOf(snap))
            }
        awaitClose { listener.remove() }
    }

    // Tozsamosc wizyty pochodzi z identyfikatora dokumentu, nigdy z pola
    // "appointmentId". Pole pisze klient, a wartosc odczytana z niego steruje
    // rezerwacja (bookAppointmentAtomically), odwolaniem, zakonczeniem, kasowaniem
    // terminu i krokiem usuwania konta (AccountDeletion.DeleteFreeSlot) — wszystkie
    // te operacje celowalyby w appointments/<pole>, czyli w dokument, ktory nie
    // istnieje. Skutki: wolny termin nie do zarezerwowania i nie do skasowania oraz
    // usuwanie konta przerwane w polowie (ten sam objaw co zadanie 11, R1).
    private fun appointmentsOf(snapshot: QuerySnapshot?): List<AppointmentModel> =
        snapshot?.documents?.mapNotNull { document ->
            document.toObject(AppointmentModel::class.java)?.copy(appointmentId = document.id)
        } ?: emptyList()

    override suspend fun bookAppointmentAtomically(
        appointmentId: String,
        patientId: String,
        psychologistId: String,
        priceCoins: Int,
        notes: String
    ) = escrow.book(appointmentId, patientId, psychologistId, priceCoins, notes)

    override suspend fun cancelBooking(appointmentId: String, cancelledBy: String): CancelOutcome =
        escrow.cancel(appointmentId, cancelledBy)

    override suspend fun refundAbandonedBooking(appointmentId: String, patientId: String): CancelOutcome =
        escrow.reclaimAbandoned(appointmentId, patientId)

    override suspend fun bookingLanded(appointmentId: String, patientId: String): Boolean =
        escrow.bookingLanded(appointmentId, patientId)

    override suspend fun completionLanded(appointmentId: String): CompletionOutcome? =
        escrow.completionLanded(appointmentId)

    override suspend fun cancellationLanded(appointmentId: String, cancelledBy: String): CancelOutcome? =
        escrow.cancellationLanded(appointmentId, cancelledBy)

    override suspend fun reclaimLanded(appointmentId: String): CancelOutcome? =
        escrow.reclaimLanded(appointmentId)

    private val completionFlow = CompletionFlow(
        store = object : CompletionStore {
            override suspend fun markerExists(markerId: String): Boolean =
                escrow.markerExists(markerId)

            override suspend fun completeWithMarker(
                appointmentId: String,
                markerId: String,
                marker: CompletionModel
            ): CompletionOutcome = escrow.complete(appointmentId, markerId to marker)

            override suspend fun completeOnly(appointmentId: String): CompletionOutcome = escrow.complete(appointmentId, null)
        },
        clock = clock
    )

    override suspend fun completeAppointment(appointmentId: String): CompletionOutcome {
        val appointment = escrow.loadAppointment(appointmentId)
            ?: throw UserMessageException("Nie znaleziono wizyty.")
        return completionFlow.complete(appointment)
    }

    override suspend fun deleteAppointment(appointmentId: String) {
        collection.document(appointmentId).delete().await()
    }

    override suspend fun getAppointmentByDateTime(psychId: String, date: String, time: String): AppointmentModel? {
        val snap = collection
            .whereEqualTo("psychologistId", psychId)
            .whereEqualTo("date", date)
            .whereEqualTo("time", time)
            .limit(1)
            .get()
            .await()
        return appointmentsOf(snap).firstOrNull()
    }
}
