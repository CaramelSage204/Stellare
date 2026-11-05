package com.example.stellare.ui.settings

sealed interface AccountDeletionState {
    data object Idle : AccountDeletionState
    data object Deleting : AccountDeletionState
    data object RecentLoginRequired : AccountDeletionState
    data class Failed(val message: String, val profileGone: Boolean) : AccountDeletionState
}
