package com.example.stellare.data.model

sealed interface CancelOutcome {
    data class Refunded(val amount: Int) : CancelOutcome
    data class Released(val amount: Int) : CancelOutcome
    data object CancelledWithoutRefund : CancelOutcome
}

object RefundPolicy {
    fun settlement(appointment: AppointmentModel, escrow: EscrowModel?, cancelledBy: String, nowMillis: Long): Settlement? {
        if (escrow == null || appointment.status != "BOOKED" || appointment.patientId == null || appointment.priceCoins <= 0) {
            return null
        }
        val cancelled = appointment.copy(status = "CANCELLED", cancelledBy = cancelledBy)
        return EscrowSettlement.decide(escrow, cancelled, cancelledBy, nowMillis)
    }
}
