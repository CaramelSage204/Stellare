package com.example.stellare.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow

@Composable
fun rememberDeposit(depositOf: (String) -> Flow<DepositState>, appointmentId: String): DepositState {
    val flow = remember(appointmentId) { depositOf(appointmentId) }
    val state by flow.collectAsStateWithLifecycle(DepositState.Loading)
    return state
}
