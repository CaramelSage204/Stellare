package com.example.stellare.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentTiming
import kotlinx.coroutines.delay

private const val MIN_TICK_MS = 1_000L

@Composable
fun rememberNowMillis(clock: () -> Long, appointments: List<AppointmentModel>): Long {
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(appointments) {
        now = clock()
        while (true) {
            val next = AppointmentTiming.nextChange(appointments, now) ?: break
            delay((next - now).coerceAtLeast(MIN_TICK_MS))
            now = clock()
        }
    }
    return now
}
