package com.example.stellare.data.model

object AppointmentTiming {
    fun isUpcoming(appointment: AppointmentModel, nowMillis: Long): Boolean =
        appointment.startsAtMillis > nowMillis

    fun canCancel(appointment: AppointmentModel, nowMillis: Long, isPsychologist: Boolean): Boolean =
        appointment.status == "BOOKED" && (isPsychologist || isUpcoming(appointment, nowMillis))

    fun isMissedWithPaidCoins(appointment: AppointmentModel, nowMillis: Long): Boolean =
        appointment.status == "BOOKED" &&
            !isUpcoming(appointment, nowMillis) &&
            appointment.patientId != null &&
            appointment.priceCoins > 0

    fun nextBoundary(appointments: List<AppointmentModel>, nowMillis: Long): Long? =
        appointments
            .filter { it.status == "BOOKED" && isUpcoming(it, nowMillis) }
            .minOfOrNull { it.startsAtMillis }

    fun nextReclaimOpening(appointments: List<AppointmentModel>, nowMillis: Long): Long? =
        appointments
            .filter { it.status == "BOOKED" && it.patientId != null && it.priceCoins > 0 }
            .map { it.startsAtMillis + EscrowSettlement.ABANDONED_BOOKING_MILLIS + 1 }
            .filter { it > nowMillis }
            .minOrNull()

    fun nextChange(appointments: List<AppointmentModel>, nowMillis: Long): Long? =
        listOfNotNull(nextBoundary(appointments, nowMillis), nextReclaimOpening(appointments, nowMillis)).minOrNull()
}
