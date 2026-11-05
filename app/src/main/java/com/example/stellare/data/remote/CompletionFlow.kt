package com.example.stellare.data.remote

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.UserMessageException
import com.example.stellare.data.model.CompletionModel
import com.example.stellare.data.model.CompletionOutcome
import kotlinx.coroutines.CancellationException

interface CompletionStore {
    suspend fun markerExists(markerId: String): Boolean
    suspend fun completeWithMarker(appointmentId: String, markerId: String, marker: CompletionModel): CompletionOutcome
    suspend fun completeOnly(appointmentId: String): CompletionOutcome
}

class CompletionFlow(
    private val store: CompletionStore,
    private val clock: () -> Long
) {
    suspend fun complete(appointment: AppointmentModel): CompletionOutcome {
        val patientId = appointment.patientId
            ?: throw UserMessageException("Wizyta nie ma przypisanego pacjenta.")
        val markerId = CompletionModel.idFor(appointment.psychologistId, patientId)

        if (store.markerExists(markerId)) {
            return store.completeOnly(appointment.appointmentId)
        }

        val marker = CompletionModel(
            psychologistId = appointment.psychologistId,
            patientId = patientId,
            appointmentId = appointment.appointmentId,
            completedAt = clock()
        )
        return try {
            store.completeWithMarker(appointment.appointmentId, markerId, marker)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!store.markerExists(markerId)) throw e
            store.completeOnly(appointment.appointmentId)
        }
    }
}
