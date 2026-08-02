package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.AppointmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments WHERE psychologistId = :psychologistId ORDER BY date ASC, time ASC")
    fun getAppointmentsForPsychologist(psychologistId: Int): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE patientId = :patientId ORDER BY date ASC, time ASC")
    fun getAppointmentsForPatient(patientId: Int): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE psychologistId = :psychologistId AND status = 'FREE' ORDER BY date ASC, time ASC")
    fun getFreeSlotsForPsychologist(psychologistId: Int): Flow<List<AppointmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: AppointmentEntity): Long

    @Query("UPDATE appointments SET patientId = :patientId, status = 'BOOKED', notes = :notes WHERE appointmentId = :appointmentId")
    suspend fun bookAppointment(appointmentId: Int, patientId: Int, notes: String)

    @Query("UPDATE appointments SET patientId = NULL, status = 'FREE', notes = '' WHERE appointmentId = :appointmentId")
    suspend fun cancelBooking(appointmentId: Int)

    @Query("UPDATE appointments SET status = 'COMPLETED' WHERE appointmentId = :appointmentId")
    suspend fun completeAppointment(appointmentId: Int)

    @Query("DELETE FROM appointments WHERE appointmentId = :appointmentId")
    suspend fun deleteAppointment(appointmentId: Int)

    @Query("SELECT * FROM appointments WHERE psychologistId = :psychologistId AND date = :date AND time = :time LIMIT 1")
    suspend fun getAppointmentByDateTime(psychologistId: Int, date: String, time: String): AppointmentEntity?
}
