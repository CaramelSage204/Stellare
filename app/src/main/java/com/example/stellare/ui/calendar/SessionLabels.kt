package com.example.stellare.ui.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentSchedule
import java.util.TimeZone

enum class SessionTone { FREE, BOOKED, COMPLETED }

object SessionLabels {

    const val FREE_TITLE = "Wolny termin"
    const val PATIENT_FALLBACK = "Pacjent"
    const val PSYCHOLOGIST_FALLBACK = "Psycholog"

    private const val MS_PER_MINUTE = 60_000L

    fun toneOf(appointment: AppointmentModel): SessionTone = when {
        appointment.status == "FREE" -> SessionTone.FREE
        appointment.status == "BOOKED" -> SessionTone.BOOKED
        else -> SessionTone.COMPLETED
    }

    fun statusLabel(appointment: AppointmentModel, isPsychologist: Boolean, canCancel: Boolean): String =
        when (appointment.status) {
            "FREE" -> "Wolny"
            "BOOKED" -> when {
                isPsychologist -> "Zarezerwowany"
                canCancel -> "Potwierdzona"
                else -> "Termin minął"
            }
            "COMPLETED" -> if (isPsychologist) "Ukończony" else "Zrealizowano"
            "CANCELLED" -> "Odwołana"
            else -> appointment.status
        }

    fun timeRange(appointment: AppointmentModel, zone: TimeZone): String {
        val end = appointment.startsAtMillis + DayTimeline.SESSION_MINUTES * MS_PER_MINUTE
        return "${AppointmentSchedule.timeOf(appointment.startsAtMillis, zone)}–${AppointmentSchedule.timeOf(end, zone)}"
    }

    fun title(appointment: AppointmentModel, isPsychologist: Boolean, names: Map<String, String>): String {
        if (appointment.status == "FREE") return FREE_TITLE
        return participantName(appointment, isPsychologist, names)
    }

    fun participantName(appointment: AppointmentModel, isPsychologist: Boolean, names: Map<String, String>): String {
        val otherId = if (isPsychologist) appointment.patientId else appointment.psychologistId
        val fallback = if (isPsychologist) PATIENT_FALLBACK else PSYCHOLOGIST_FALLBACK
        return otherId?.let { names[it] } ?: fallback
    }

    fun hourLabel(hour: Int): String = hour.toString().padStart(2, '0') + ":00"
}
