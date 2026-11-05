package com.example.stellare.ui.calendar.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.stellare.data.model.AppointmentSchedule
import com.example.stellare.ui.calendar.CalendarDays
import java.util.TimeZone
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun DayNavigator(
    selectedDayMillis: Long,
    todayMillis: Long,
    zone: TimeZone,
    onSelectDay: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val days = remember(selectedDayMillis, todayMillis, zone) {
        CalendarDays.strip(selectedDayMillis, todayMillis, zone)
    }
    val isTodaySelected = CalendarDays.isSameDay(selectedDayMillis, todayMillis, zone)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppointmentSchedule.displayDate(AppointmentSchedule.dateOf(selectedDayMillis, zone)),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isTodaySelected) {
                    TextButton(onClick = { onSelectDay(todayMillis) }) { Text("Dziś", maxLines = 1) }
                }
                IconButton(onClick = {
                    onSelectDay(CalendarDays.shiftWeek(selectedDayMillis, forward = false, todayMillis = todayMillis, zone = zone))
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Poprzedni tydzień")
                }
                IconButton(onClick = {
                    onSelectDay(CalendarDays.shiftWeek(selectedDayMillis, forward = true, todayMillis = todayMillis, zone = zone))
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Następny tydzień")
                }
            }
        }
        DayStrip(days = days, onSelectDay = onSelectDay)
    }
}
