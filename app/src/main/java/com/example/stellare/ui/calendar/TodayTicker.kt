package com.example.stellare.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.util.TimeZone

private const val MIDNIGHT_MARGIN_MS = 1_000L

@Composable
fun rememberTodayMillis(clock: () -> Long, zone: TimeZone): Long {
    var today by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(clock, zone) {
        while (true) {
            delay(CalendarDays.millisUntilNextMidnight(clock(), zone) + MIDNIGHT_MARGIN_MS)
            today = clock()
        }
    }
    return today
}
