package com.example.stellare.data.model

sealed interface CompletionOutcome {
    data class Released(val amount: Int) : CompletionOutcome
    data object NoCoinsMoved : CompletionOutcome
}
