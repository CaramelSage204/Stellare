package com.example.stellare.ui.calendar.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentSchedule
import java.util.TimeZone
import com.example.stellare.ui.theme.stellareButtonColors
import com.example.stellare.ui.LocalClock

@Composable
fun AddSlotDialog(
    onDismiss: () -> Unit,
    onConfirm: (startsAtMillis: Long) -> Unit,
    existingAppointments: List<AppointmentModel>
) {
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedHour by rememberSaveable { mutableStateOf(12) }
    var selectedMinute by rememberSaveable { mutableStateOf(0) }

    val formattedTimeCheck = "${selectedHour.toString().padStart(2, '0')}:${selectedMinute.toString().padStart(2, '0')}"
    val isAlreadyBookedOrFree = selectedDate != null && existingAppointments.any { 
        it.date == selectedDate && it.time == formattedTimeCheck
    }

    val candidateStartsAt = selectedDate?.let {
        AppointmentSchedule.startsAtMillis(it, selectedHour, selectedMinute, TimeZone.getDefault())
    }
    val isInPast = candidateStartsAt != null && candidateStartsAt <= LocalClock.current()
    val canConfirm = candidateStartsAt != null && !isAlreadyBookedOrFree && !isInPast

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = "Dodaj wolny termin",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Wybierz dzień z kalendarza, a następnie ustaw godzinę sesji.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                
                StellareCalendar(
                    selectedDate = selectedDate,
                    onDateSelected = { date ->
                        selectedDate = date
                    }
                )
                
                AnimatedVisibility(
                    visible = selectedDate != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Wybierz godzinę i minutę:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        
                        WheelTimePicker(
                            selectedHour = selectedHour,
                            selectedMinute = selectedMinute,
                            onTimeSelected = { hour, minute ->
                                selectedHour = hour
                                selectedMinute = minute
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (isInPast) {
                            Text(
                                text = "Ten termin jest już w przeszłości. Wybierz późniejszą godzinę.",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        selectedDate?.let { date ->
                            if (isAlreadyBookedOrFree) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Masz już dodany lub zaplanowany termin o $formattedTimeCheck w tym dniu!",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            } else {
                                val dateParts = date.split("-")
                                val displayDate = if (dateParts.size == 3) "${dateParts[2]}.${dateParts[1]}.${dateParts[0]}" else date
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Wybrany termin: $displayDate o $formattedTimeCheck",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canConfirm) {
                        onConfirm(candidateStartsAt!!)
                    }
                },
                enabled = canConfirm,
                colors = stellareButtonColors()
            ) {
                Text("Dodaj do grafiku")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}
