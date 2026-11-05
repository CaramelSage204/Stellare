package com.example.stellare.ui.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentTiming
import com.example.stellare.data.model.EscrowModel

sealed interface DepositState {
    data object Loading : DepositState
    data object Unavailable : DepositState
    data class Known(val escrow: EscrowModel?) : DepositState

    val held: EscrowModel?
        get() = (this as? Known)?.escrow?.takeIf { it.status == EscrowModel.HELD }

    fun missedVisitRefund(appointment: AppointmentModel, nowMillis: Long): Int? =
        held?.amount?.takeIf { AppointmentTiming.isMissedWithPaidCoins(appointment, nowMillis) }
}
