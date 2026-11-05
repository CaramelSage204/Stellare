package com.example.stellare.ui.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.CompletionOutcome
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.EscrowSettlement
import com.example.stellare.data.model.RefundPolicy
import com.example.stellare.data.model.Settlement

object RefundMessages {

    const val CHECKING = "Sprawdzam, co stanie się z monetami z tej wizyty…"
    const val UNAVAILABLE = "Nie udało się sprawdzić depozytu tej wizyty, więc nie wiemy, co stanie się z monetami. Zamknij to okno i otwórz je ponownie."
    const val NO_COINS = "Ta wizyta nie wiąże się z żadnymi monetami."
    const val NO_DEPOSIT = "Ta wizyta nie ma depozytu, więc jej odwołanie nie przeniesie żadnych monet."
    const val SETTLED_DEPOSIT = "Depozyt tej wizyty został już rozliczony, więc jej odwołanie nie przeniesie żadnych monet."

    const val NO_DEPOSIT_ON_COMPLETE = "Ta wizyta nie ma depozytu, więc jej zakończenie nie przeniesie żadnych monet."
    const val SETTLED_ON_COMPLETE = "Depozyt tej wizyty został już rozliczony, więc jej zakończenie nie przeniesie żadnych monet."

    fun beforeCancel(appointment: AppointmentModel, deposit: DepositState, viewerIsPsychologist: Boolean, nowMillis: Long): String =
        when {
            appointment.priceCoins <= 0 -> NO_COINS
            deposit is DepositState.Loading -> CHECKING
            deposit is DepositState.Unavailable -> UNAVAILABLE
            else -> known(appointment, (deposit as DepositState.Known).escrow, viewerIsPsychologist, nowMillis)
        }

    private fun known(appointment: AppointmentModel, escrow: EscrowModel?, viewerIsPsychologist: Boolean, nowMillis: Long): String {
        val cancelledBy = if (viewerIsPsychologist) appointment.psychologistId else appointment.patientId.orEmpty()
        if (escrow == null) return NO_DEPOSIT
        if (escrow.status != EscrowModel.HELD) return SETTLED_DEPOSIT
        return when (val settlement = RefundPolicy.settlement(appointment, escrow, cancelledBy, nowMillis)) {
            is Settlement.RefundToPatient ->
                if (viewerIsPsychologist) {
                    "Pacjent od razu dostanie zwrot ${settlement.amount} monet z depozytu wizyty."
                } else {
                    "Do sesji zostało ponad 24 godziny — ${settlement.amount} monet wróci na Twoje konto od razu."
                }
            is Settlement.ReleaseToPsychologist ->
                "Do sesji zostało mniej niż 24 godziny — monety nie wracają: opłata ${settlement.amount} monet trafi do psychologa."
            is Settlement.NotSettleable -> settlement.reason
            null -> NO_DEPOSIT
        }
    }

    fun beforeComplete(appointment: AppointmentModel, deposit: DepositState, nowMillis: Long): String =
        when {
            appointment.priceCoins <= 0 -> NO_COINS
            deposit is DepositState.Loading -> CHECKING
            deposit is DepositState.Unavailable -> UNAVAILABLE
            else -> completion(appointment, (deposit as DepositState.Known).escrow, nowMillis)
        }

    private fun completion(appointment: AppointmentModel, escrow: EscrowModel?, nowMillis: Long): String {
        if (escrow == null) return NO_DEPOSIT_ON_COMPLETE
        if (escrow.status != EscrowModel.HELD) return SETTLED_ON_COMPLETE
        return when (val settlement = EscrowSettlement.decide(escrow, appointment.copy(status = "COMPLETED"), appointment.psychologistId, nowMillis)) {
            is Settlement.ReleaseToPsychologist -> "${settlement.amount} monet z depozytu wizyty trafi na Twoje konto."
            is Settlement.NotSettleable -> settlement.reason
            is Settlement.RefundToPatient -> SETTLED_ON_COMPLETE
        }
    }

    fun afterCancel(outcome: CancelOutcome, viewerIsPsychologist: Boolean): String = when (outcome) {
        is CancelOutcome.Refunded ->
            if (viewerIsPsychologist) {
                "Wizyta odwołana. Pacjent otrzymał zwrot ${outcome.amount} monet."
            } else {
                "Wizyta odwołana. ${outcome.amount} monet wróciło na Twoje konto."
            }
        is CancelOutcome.Released -> "Wizyta odwołana. Opłata ${outcome.amount} monet nie została zwrócona i trafiła do psychologa."
        CancelOutcome.CancelledWithoutRefund -> "Wizyta odwołana. Żadne monety nie zostały przeniesione."
    }

    fun afterComplete(outcome: CompletionOutcome): String = when (outcome) {
        is CompletionOutcome.Released -> "Wizyta zakończona. ${outcome.amount} monet z depozytu trafiło na Twoje konto."
        CompletionOutcome.NoCoinsMoved -> "Wizyta zakończona. Żadne monety nie zostały przeniesione."
    }

    fun afterReclaim(outcome: CancelOutcome): String = when (outcome) {
        is CancelOutcome.Refunded -> "Odzyskano ${outcome.amount} monet. Są już na Twoim koncie."
        else -> "Nie zwrócono żadnych monet."
    }
}
