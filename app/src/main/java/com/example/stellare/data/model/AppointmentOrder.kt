package com.example.stellare.data.model

object AppointmentOrder {

    fun chronological(appointments: List<AppointmentModel>): List<AppointmentModel> =
        appointments.sortedBy { it.startsAtMillis }

    fun upcoming(appointments: List<AppointmentModel>, nowMillis: Long): List<AppointmentModel> =
        appointments.filter { isUpcoming(it, nowMillis) }.sortedBy { it.startsAtMillis }

    fun freeUpcomingCount(appointments: List<AppointmentModel>, nowMillis: Long): Int =
        upcoming(appointments.filter { it.status == "FREE" }, nowMillis).size

    private fun isUpcoming(appointment: AppointmentModel, nowMillis: Long): Boolean =
        appointment.startsAtMillis > nowMillis
}