package com.example.stellare.ui.settings

sealed interface BalanceState {
    data object Loading : BalanceState
    data object Missing : BalanceState
    data object Unavailable : BalanceState
    data class Known(val coins: Int) : BalanceState
}
