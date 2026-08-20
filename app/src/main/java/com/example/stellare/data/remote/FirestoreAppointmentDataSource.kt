package com.example.stellare.data.remote

import com.example.stellare.data.model.AppointmentModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreAppointmentDataSource {
    private val db = Firebase.firestore
    private val collection = db.collection("appointments")

    suspend fun saveAppointment(appointment: AppointmentModel) {
        val id = if (appointment.appointmentId.isEmpty()) {
            collection.document().id
        } else {
            appointment.appointmentId
        }
        val appointmentToSave = appointment.copy(appointmentId = id)
        collection.document(id).set(appointmentToSave).await()
    }

    fun getAppointmentsForPsychologist(psychologistId: String): Flow<List<AppointmentModel>> = callbackFlow {
        val listener = collection
            .whereEqualTo("psychologistId", psychologistId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.toObjects(AppointmentModel::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    fun getAppointmentsForPatient(patientId: String): Flow<List<AppointmentModel>> = callbackFlow {
        val listener = collection
            .whereEqualTo("patientId", patientId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.toObjects(AppointmentModel::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    fun getFreeSlotsForPsychologist(psychologistId: String): Flow<List<AppointmentModel>> = callbackFlow {
        val listener = collection
            .whereEqualTo("psychologistId", psychologistId)
            .whereEqualTo("status", "FREE")
            .addSnapshotListener { snap, _ ->
                trySend(snap?.toObjects(AppointmentModel::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun bookAppointment(appointmentId: String, patientId: String, notes: String) {
        collection.document(appointmentId).update(
            "patientId", patientId,
            "status", "BOOKED",
            "notes", notes
        ).await()
    }

    suspend fun cancelBooking(appointmentId: String) {
        collection.document(appointmentId).update(
            "patientId", null,
            "status", "FREE",
            "notes", ""
        ).await()
    }

    suspend fun completeAppointment(appointmentId: String) {
        collection.document(appointmentId).update("status", "COMPLETED").await()
    }

    suspend fun deleteAppointment(appointmentId: String) {
        collection.document(appointmentId).delete().await()
    }

    suspend fun getAppointmentByDateTime(psychId: String, date: String, time: String): AppointmentModel? {
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
