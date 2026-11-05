package com.example.stellare.ui.profile

sealed interface ReviewSubmitState {
    data object Idle : ReviewSubmitState
    data object InProgress : ReviewSubmitState
    data object Success : ReviewSubmitState
    data class Error(val message: String) : ReviewSubmitState
}
