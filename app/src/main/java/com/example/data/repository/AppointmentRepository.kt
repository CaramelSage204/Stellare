package com.example.data.repository

import com.example.data.local.dao.AppointmentDao
import com.example.data.local.entity.AppointmentEntity
import kotlinx.coroutines.flow.Flow

class AppointmentRepository(private val appointmentDao: AppointmentDao) {
    fun getAppointmentsForPsychologistFlow(psychId: Int): Flow<List<AppointmentEntity>> = appointmentDao.getAppointmentsForPsychologist(psychId)
    fun getAppointmentsForPatientFlow(patientId: Int): Flow<List<AppointmentEntity>> = appointmentDao.getAppointmentsForPatient(patientId)
    fun getFreeSlotsForPsychologistFlow(psychId: Int): Flow<List<AppointmentEntity>> = appointmentDao.getFreeSlotsForPsychologist(psychId)
    suspend fun insertAppointment(appointment: AppointmentEntity): Long = appointmentDao.insertAppointment(appointment)
    suspend fun bookAppointment(appointmentId: Int, patientId: Int, notes: String) = appointmentDao.bookAppointment(appointmentId, patientId, notes)
    suspend fun cancelBooking(appointmentId: Int) = appointmentDao.cancelBooking(appointmentId)
    suspend fun completeAppointment(appointmentId: Int) = appointmentDao.completeAppointment(appointmentId)
    suspend fun deleteAppointment(appointmentId: Int) = appointmentDao.deleteAppointment(appointmentId)
    suspend fun getAppointmentByDateTime(psychId: Int, date: String, time: String): AppointmentEntity? = appointmentDao.getAppointmentByDateTime(psychId, date, time)
}
