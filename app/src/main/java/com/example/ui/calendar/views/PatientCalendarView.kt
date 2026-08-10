package com.example.ui.calendar.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AppointmentEntity
import com.example.data.local.entity.UserEntity
import com.example.ui.calendar.components.AppointmentCard

@Composable
fun PatientCalendarView(
    appointments: List<AppointmentEntity>,
    participants: Map<Int, UserEntity>,
    onCancelAppointment: (Int) -> Unit
) {
    var showCancelConfirmDialog by remember { mutableStateOf<AppointmentEntity?>(null) }

    val upcomingSessions = appointments.filter { it.status == "BOOKED" }
    val pastSessions = appointments.filter { it.status == "COMPLETED" }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Nadchodzące spotkania (${upcomingSessions.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (upcomingSessions.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.EventBusy, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Brak zaplanowanych wizyt.", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Wejdź w profil wybranego psychologa z listy głównej i zarezerwuj dogodny wolny termin.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(upcomingSessions) { appointment ->
                    val psychUser = participants[appointment.psychologistId]
                    AppointmentCard(
                        appointment = appointment,
                        otherUser = psychUser,
                        isPsychologist = false,
                        onCancel = { showCancelConfirmDialog = appointment }
                    )
                }
            }
        }

        // History / Past Sessions
        if (pastSessions.isNotEmpty()) {
            Text(
                text = "Historia sesji (${pastSessions.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(0.7f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pastSessions) { appointment ->
                    val psychUser = participants[appointment.psychologistId]
                    AppointmentCard(
                        appointment = appointment,
                        otherUser = psychUser,
                        isPsychologist = false
                    )
                }
            }
        }
    }

    showCancelConfirmDialog?.let { app ->
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = null },
            title = { Text("Potwierdź odwołanie wizyty") },
            text = {
                val dateParts = app.date.split("-")
                val displayDate = if (dateParts.size == 3) "${dateParts[2]}.${dateParts[1]}.${dateParts[0]}" else app.date
                Text(
                    text = "Czy na pewno chcesz odwołać wizytę zaplanowaną na $displayDate o godzinie ${app.time}?\n\n" +
                           "Status wizyty zostanie cofnięty do wolnego slotu, a pacjent zostanie powiadomiony.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelAppointment(app.appointmentId)
                        showCancelConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Odwołaj wizytę", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = null }) {
                    Text("Anuluj")
                }
            }
        )
    }
}
