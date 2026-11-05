package com.example.stellare.data.repository

import com.example.stellare.data.remote.AppointmentDataSource
import com.example.stellare.data.remote.EscrowDataSource
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.CompletionOutcome
import kotlinx.coroutines.flow.Flow

class AppointmentRepository(
    private val appointmentDataSource: AppointmentDataSource,
    private val escrowDataSource: EscrowDataSource
) {

    fun getAppointmentsForPsychologistFlow(psychId: String): Flow<List<AppointmentModel>> =
        appointmentDataSource.getAppointmentsForPsychologist(psychId)

    fun getAppointmentsForPatientFlow(patientId: String): Flow<List<AppointmentModel>> =
        appointmentDataSource.getAppointmentsForPatient(patientId)

    fun getFreeSlotsForPsychologistFlow(psychId: String): Flow<List<AppointmentModel>> =
        appointmentDataSource.getFreeSlotsForPsychologist(psychId)

    suspend fun insertAppointment(appointment: AppointmentModel) =
        appointmentDataSource.saveAppointment(appointment)

    suspend fun bookAppointment(
        appointmentId: String,
        patientId: String,
        psychologistId: String,
        priceCoins: Int,
        notes: String
    ) = appointmentDataSource.bookAppointmentAtomically(
        appointmentId, patientId, psychologistId, priceCoins, notes
    )

    suspend fun cancelBooking(appointmentId: String, cancelledBy: String): CancelOutcome =
        appointmentDataSource.cancelBooking(appointmentId, cancelledBy)

    suspend fun refundAbandonedBooking(appointmentId: String, patientId: String): CancelOutcome =
        appointmentDataSource.refundAbandonedBooking(appointmentId, patientId)

    fun observeEscrow(appointmentId: String): Flow<EscrowModel?> =
        escrowDataSource.observeEscrow(appointmentId)

    suspend fun completeAppointment(appointmentId: String) =
        appointmentDataSource.completeAppointment(appointmentId)

    suspend fun deleteAppointment(appointmentId: String) =
        appointmentDataSource.deleteAppointment(appointmentId)

    suspend fun bookingLanded(appointmentId: String, patientId: String): Boolean =
        appointmentDataSource.bookingLanded(appointmentId, patientId)

    suspend fun completionLanded(appointmentId: String): CompletionOutcome? =
        appointmentDataSource.completionLanded(appointmentId)

    suspend fun cancellationLanded(appointmentId: String, cancelledBy: String): CancelOutcome? =
        appointmentDataSource.cancellationLanded(appointmentId, cancelledBy)

    suspend fun reclaimLanded(appointmentId: String): CancelOutcome? =
        appointmentDataSource.reclaimLanded(appointmentId)

    suspend fun getAppointmentByDateTime(psychId: String, date: String, time: String): AppointmentModel? =
        appointmentDataSource.getAppointmentByDateTime(psychId, date, time)
}