package com.example.stellare.ui.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.EscrowSettlement
import com.example.stellare.data.model.Settlement

object ReclaimOffer {

    fun candidates(appointments: List<AppointmentModel>, userId: String?, nowMillis: Long): List<AppointmentModel> =
        appointments.filter {
            it.status == "BOOKED" &&
                userId != null &&
                it.patientId == userId &&
                it.priceCoins > 0 &&
                EscrowSettlement.isAbandoned(it, nowMillis)
        }

    fun amount(appointment: AppointmentModel, deposit: DepositState, userId: String, nowMillis: Long): Int? {
        val escrow = (deposit as? DepositState.Known)?.escrow ?: return null
        return (EscrowSettlement.decide(escrow, appointment, userId, nowMillis) as? Settlement.RefundToPatient)?.amount
    }
}
