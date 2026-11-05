package com.example.stellare.data.model

import java.util.TimeZone

data class AppointmentModel(
    val appointmentId: String = "",
    val psychologistId: String = "",
    val patientId: String? = null,
    val startsAtMillis: Long = 0L,
    val date: String = "",
    val time: String = "",
    val notes: String = "",
    val status: String = "FREE",
    val priceCoins: Int = 0,
    val cancelledBy: String? = null
) {
    companion object {
        fun freeSlot(
            psychologistId: String,
            startsAtMillis: Long,
            priceCoins: Int,
            zone: TimeZone
        ): AppointmentModel = AppointmentModel(
            psychologistId = psychologistId,
            startsAtMillis = startsAtMillis,
            date = AppointmentSchedule.dateOf(startsAtMillis, zone),
            time = AppointmentSchedule.timeOf(startsAtMillis, zone),
            status = "FREE",
            priceCoins = priceCoins
        )
    }
}
