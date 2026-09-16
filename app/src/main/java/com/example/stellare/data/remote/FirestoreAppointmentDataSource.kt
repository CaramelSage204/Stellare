package com.example.stellare.data.remote

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.model.WalletTransactionType
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreAppointmentDataSource : AppointmentDataSource {
    private val db = Firebase.firestore
    private val collection = db.collection("appointments")

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
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(AppointmentModel::class.java) ?: emptyList())
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
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(AppointmentModel::class.java) ?: emptyList())
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
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(AppointmentModel::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    override suspend fun bookAppointmentAtomically(
        appointmentId: String,
        patientId: String,
        psychologistId: String,
        priceCoins: Int,
        notes: String
    ) {
        val appointmentRef = collection.document(appointmentId)
        val patientRef = db.collection("users").document(patientId)
        val psychologistRef = db.collection("users").document(psychologistId)
        val ledger = db.collection("wallet_transactions")
        val paymentRef = ledger.document()
        val earningRef = ledger.document("${appointmentId}_${psychologistId}")
        val now = System.currentTimeMillis()

        db.runTransaction { transaction ->
            val appointment = transaction.get(appointmentRef)
            val patient = transaction.get(patientRef)
            val psychologist = transaction.get(psychologistRef)

            if (appointment.getString("status") != "FREE") {
                throw IllegalStateException("Termin został już zarezerwowany.")
            }

            val patientBalance = patient.getLong("coinsBalance") ?: 0L
            if (patientBalance < priceCoins) {
                throw IllegalStateException("Za mało monet w portfelu.")
            }
            val psychologistBalance = psychologist.getLong("coinsBalance") ?: 0L

            transaction.update(
                appointmentRef,
                mapOf("patientId" to patientId, "status" to "BOOKED", "notes" to notes)
            )
            transaction.update(
                patientRef,
                mapOf(
                    "coinsBalance" to patientBalance - priceCoins,
                    "lastTransferTo" to psychologistId,
                    "lastTransferAmount" to priceCoins
                )
            )
            transaction.update(psychologistRef, "coinsBalance", psychologistBalance + priceCoins)

            transaction.set(
                paymentRef,
                WalletTransactionModel(
                    transactionId = paymentRef.id,
                    userId = patientId,
                    counterpartyId = psychologistId,
                    appointmentId = appointmentId,
                    amount = -priceCoins,
                    type = WalletTransactionType.BOOKING_PAYMENT,
                    title = "Opłata za wizytę",
                    timestamp = now
                )
            )
            transaction.set(
                earningRef,
                WalletTransactionModel(
                    transactionId = earningRef.id,
                    userId = psychologistId,
                    counterpartyId = patientId,
                    appointmentId = appointmentId,
                    amount = priceCoins,
                    type = WalletTransactionType.BOOKING_EARNING,
                    title = "Wpływ za wizytę",
                    timestamp = now
                )
            )
            null
        }.await()
    }

    override suspend fun cancelBooking(appointmentId: String) {
        collection.document(appointmentId).update(
            "patientId", null,
            "status", "FREE",
            "notes", ""
        ).await()
    }

    override suspend fun completeAppointment(appointmentId: String) {
        collection.document(appointmentId).update("status", "COMPLETED").await()
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
        return snap.toObjects(AppointmentModel::class.java).firstOrNull()
    }
}
