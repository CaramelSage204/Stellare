package com.example.stellare.ui.calendar

import com.example.stellare.data.model.AppointmentModel

object ConfirmationTarget {

    enum class Operation(val allows: (AppointmentModel) -> Boolean) {
        CANCEL({ it.status == "BOOKED" }),
        COMPLETE({ it.status == "BOOKED" }),
        DELETE({ it.status == "FREE" }),
        BOOK({ it.status == "FREE" })
    }

    fun current(id: String?, appointments: List<AppointmentModel>): AppointmentModel? =
        if (id == null) null else appointments.firstOrNull { it.appointmentId == id }

    fun resolve(id: String?, appointments: List<AppointmentModel>, operation: Operation): AppointmentModel? =
        current(id, appointments)?.takeIf(operation.allows)
}
