package com.example.stellare.ui.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*
import java.text.SimpleDateFormat

@Composable
fun StellareCalendar(
    selectedDate: String?, // format: "yyyy-MM-dd"
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val today = Calendar.getInstance()
    val todayStr = sdf.format(today.time)
    
    var currentMonthCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply {
            if (selectedDate != null) {
                val parts = selectedDate.split("-")
                if (parts.size == 3) {
                    set(Calendar.YEAR, parts[0].toInt())
                    set(Calendar.MONTH, parts[1].toInt() - 1)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
            } else {
                set(Calendar.DAY_OF_MONTH, 1)
            }
        })
    }

    val months = listOf(
        "Styczeń", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec",
        "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień"
    )
    val monthIndex = currentMonthCalendar.get(Calendar.MONTH)
    val year = currentMonthCalendar.get(Calendar.YEAR)
    val monthTitle = "${months[monthIndex]} $year"
    
    val daysInMonth = currentMonthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    
    // 1st day of month
    val firstDayOfMonth = currentMonthCalendar.clone() as Calendar
    firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)
    
    // Day of week (1=Sun, 2=Mon, ..., 7=Sat)
    val dayOfWeek = firstDayOfMonth.get(Calendar.DAY_OF_WEEK)
    
    // Convert to 0=Mon, 1=Tue, ..., 6=Sun
    val leadingEmptySlots = (dayOfWeek + 5) % 7
    
    val daysOfWeek = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "Sb", "Nd")
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // Calendar Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val newCal = currentMonthCalendar.clone() as Calendar
                    newCal.add(Calendar.MONTH, -1)
                    currentMonthCalendar = newCal
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Poprzedni", tint = MaterialTheme.colorScheme.primary)
            }
            
            Text(
                text = monthTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
            
            IconButton(
                onClick = {
                    val newCal = currentMonthCalendar.clone() as Calendar
                    newCal.add(Calendar.MONTH, 1)
                    currentMonthCalendar = newCal
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Następny", tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        // Days of week header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            daysOfWeek.forEach { dayName ->
                Text(
                    text = dayName,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        
        // Days Grid
        val totalSlots = daysInMonth + leadingEmptySlots
        val rows = (totalSlots + 6) / 7
        
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (c in 0 until 7) {
                    val index = r * 7 + c
                    val dayNum = index - leadingEmptySlots + 1
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (index >= leadingEmptySlots && dayNum <= daysInMonth) {
                            val dateCal = currentMonthCalendar.clone() as Calendar
                            dateCal.set(Calendar.DAY_OF_MONTH, dayNum)
                            val dateStr = sdf.format(dateCal.time)
                            
                            val isSelected = selectedDate == dateStr
                            val isToday = todayStr == dateStr
                            val isPast = dateCal.before(Calendar.getInstance().apply { 
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            })
                            
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            !isPast -> MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                            else -> Color.Transparent
                                        },
                                        shape = CircleShape
                                    )
                                    .clickable(enabled = !isPast) {
                                        onDateSelected(dateStr)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayNum.toString(),
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> Color.Black
                                        !isPast -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                    },
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
