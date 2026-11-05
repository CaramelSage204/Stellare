package com.example.stellare.ui.calendar

import com.example.stellare.data.model.AppointmentModel

object AxisAppointments {

    private val patientStatuses = setOf("BOOKED", "COMPLETED")
    private val psychologistStatuses = setOf("BOOKED", "COMPLETED", "FREE")

    fun forAxis(appointments: List<AppointmentModel>, isPsychologist: Boolean, userId: String?): List<AppointmentModel> =
        if (isPsychologist) {
            appointments.filter { it.status in psychologistStatuses }
        } else {
            appointments.filter { it.status in patientStatuses && userId != null && it.patientId == userId }
        }
}
