package com.example.stellare.ui.calendar

import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.CompletionOutcome

sealed interface TransferState {
    data object Idle : TransferState
    data object InProgress : TransferState
    data class Cancelled(val outcome: CancelOutcome) : TransferState
    data class Reclaimed(val outcome: CancelOutcome) : TransferState
    data class Completed(val outcome: CompletionOutcome) : TransferState
    data class Failed(val message: String) : TransferState
}
