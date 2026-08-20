package com.example.stellare.data.repository

import com.example.stellare.data.remote.FirestoreAppointmentDataSource
import com.example.stellare.data.model.AppointmentModel
import kotlinx.coroutines.flow.Flow

class AppointmentRepository(private val appointmentDataSource: FirestoreAppointmentDataSource) {

    fun getAppointmentsForPsychologistFlow(psychId: String): Flow<List<AppointmentModel>> =
        appointmentDataSource.getAppointmentsForPsychologist(psychId)

    fun getAppointmentsForPatientFlow(patientId: String): Flow<List<AppointmentModel>> =
        appointmentDataSource.getAppointmentsForPatient(patientId)

    fun getFreeSlotsForPsychologistFlow(psychId: String): Flow<List<AppointmentModel>> =
        appointmentDataSource.getFreeSlotsForPsychologist(psychId)

    suspend fun insertAppointment(appointment: AppointmentModel) =
        appointmentDataSource.saveAppointment(appointment)

    suspend fun bookAppointment(appointmentId: String, patientId: String, notes: String) =
        appointmentDataSource.bookAppointment(appointmentId, patientId, notes)

    suspend fun cancelBooking(appointmentId: String) =
        appointmentDataSource.cancelBooking(appointmentId)

    suspend fun completeAppointment(appointmentId: String) =
        appointmentDataSource.completeAppointment(appointmentId)

    suspend fun deleteAppointment(appointmentId: String) =
        appointmentDataSource.deleteAppointment(appointmentId)

    suspend fun getAppointmentByDateTime(psychId: String, date: String, time: String): AppointmentModel? =
        appointmentDataSource.getAppointmentByDateTime(psychId, date, time)
}