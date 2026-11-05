package com.example.stellare.data.model

sealed interface Settlement {
    data class ReleaseToPsychologist(val amount: Int) : Settlement
    data class RefundToPatient(val amount: Int) : Settlement
    data class NotSettleable(val reason: String) : Settlement
}

object EscrowSettlement {
    const val PATIENT_WINDOW_MILLIS = 24L * 60 * 60 * 1000
    const val ABANDONED_BOOKING_MILLIS = 14L * 24 * 60 * 60 * 1000

    fun isAbandoned(appointment: AppointmentModel, nowMillis: Long): Boolean =
        nowMillis - appointment.startsAtMillis > ABANDONED_BOOKING_MILLIS

    fun decide(escrow: EscrowModel, appointment: AppointmentModel, settledBy: String, nowMillis: Long): Settlement {
        if (escrow.status != EscrowModel.HELD) return Settlement.NotSettleable("Depozyt został już rozliczony.")
        if (escrow.amount <= 0) return Settlement.NotSettleable("Depozyt nie ma dodatniej kwoty.")
        val byPsychologist = settledBy == escrow.psychologistId
        val byPatient = settledBy == escrow.patientId
        if (!byPsychologist && !byPatient) return Settlement.NotSettleable("Tylko strona wizyty może rozliczyć depozyt.")
        return when (appointment.status) {
            "COMPLETED" -> completed(escrow, byPsychologist)
            "CANCELLED" -> cancelled(escrow, appointment, byPsychologist, nowMillis)
            "BOOKED" -> abandoned(escrow, appointment, byPatient, nowMillis)
            else -> Settlement.NotSettleable("Wizyta nie jest w stanie pozwalającym rozliczyć depozyt.")
        }
    }

    private fun completed(escrow: EscrowModel, byPsychologist: Boolean): Settlement =
        if (byPsychologist) {
            Settlement.ReleaseToPsychologist(escrow.amount)
        } else {
            Settlement.NotSettleable("Odbytą wizytę rozlicza psycholog.")
        }

    private fun cancelled(
        escrow: EscrowModel,
        appointment: AppointmentModel,
        byPsychologist: Boolean,
        nowMillis: Long
    ): Settlement {
        if (byPsychologist) return Settlement.RefundToPatient(escrow.amount)
        if (appointment.cancelledBy == escrow.psychologistId) return Settlement.RefundToPatient(escrow.amount)
        if (appointment.startsAtMillis - nowMillis > PATIENT_WINDOW_MILLIS) return Settlement.RefundToPatient(escrow.amount)
        if (appointment.cancelledBy == escrow.patientId) return Settlement.ReleaseToPsychologist(escrow.amount)
        return Settlement.NotSettleable("Nie wiadomo, kto odwołał wizytę.")
    }

    private fun abandoned(
        escrow: EscrowModel,
        appointment: AppointmentModel,
        byPatient: Boolean,
        nowMillis: Long
    ): Settlement =
        if (byPatient && isAbandoned(appointment, nowMillis)) {
            Settlement.RefundToPatient(escrow.amount)
        } else {
            Settlement.NotSettleable("Wizyta jeszcze nie przepadła, a psycholog może ją oznaczyć jako odbytą.")
        }
}
