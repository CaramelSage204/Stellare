package com.example.stellare.ui.calendar

sealed interface BookingState {
    data object Idle : BookingState
    data object InProgress : BookingState
    data object Success : BookingState
    data class Error(val message: String) : BookingState
}
