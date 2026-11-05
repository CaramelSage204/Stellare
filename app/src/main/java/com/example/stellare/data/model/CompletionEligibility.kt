package com.example.stellare.data.model

sealed interface CompletionEligibility {
    data object Allowed : CompletionEligibility
    data class NotYet(val startsAtMillis: Long) : CompletionEligibility

    val isAllowed: Boolean get() = this is Allowed

    companion object {
        fun of(appointment: AppointmentModel, nowMillis: Long): CompletionEligibility =
            if (appointment.startsAtMillis <= nowMillis) Allowed else NotYet(appointment.startsAtMillis)
    }
}
