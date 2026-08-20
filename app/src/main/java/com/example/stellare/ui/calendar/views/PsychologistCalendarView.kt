package com.example.stellare.ui.calendar.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.ui.calendar.components.AddSlotDialog
import com.example.stellare.ui.calendar.components.AppointmentCard
import com.example.stellare.ui.calendar.components.StatBox

@Composable
fun PsychologistCalendarView(
    appointments: List<AppointmentModel>,
    participants: Map<String, UserModel>,
    showAddSlotDialog: Boolean,
    onAddSlotDismiss: () -> Unit,
    onAddSlotConfirm: (String, String) -> Unit,
    onCompleteAppointment: (String) -> Unit,
    onCancelAppointment: (String) -> Unit,
    onDeleteAppointment: (String) -> Unit
) {
    var showCancelConfirmDialog by remember { mutableStateOf<AppointmentModel?>(null) }
    var showCompleteConfirmDialog by remember { mutableStateOf<AppointmentModel?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<AppointmentModel?>(null) }

    val bookedSlots = appointments.count { it.status == "BOOKED" }
    val completedSlots = appointments.count { it.status == "COMPLETED" }
    val freeSlots = appointments.count { it.status == "FREE" }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatBox(
                modifier = Modifier.weight(1f),
                title = "Wolne",
                value = freeSlots.toString(),
                color = MaterialTheme.colorScheme.primary
            )
            StatBox(
                modifier = Modifier.weight(1f),
                title = "Rezerwacje",
                value = bookedSlots.toString(),
                color = MaterialTheme.colorScheme.secondary
            )
            StatBox(
                modifier = Modifier.weight(1f),
                title = "Zakończone",
                value = completedSlots.toString(),
                color = Color.Gray
            )
        }

        Text(
            text = "Lista wszystkich terminów",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (appointments.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EventBusy, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Twój kalendarz jest pusty.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Kliknij przycisk +, aby dodać pierwsze wolne godziny.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(appointments) { appointment ->
                    val patientUser = appointment.patientId?.let { participants[it] }
                    AppointmentCard(
                        appointment = appointment,
                        otherUser = patientUser,
                        isPsychologist = true,
                        onComplete = { showCompleteConfirmDialog = appointment },
                        onCancel = { showCancelConfirmDialog = appointment },
                        onDelete = { showDeleteConfirmDialog = appointment }
                    )
                }
            }
        }
    }

    if (showAddSlotDialog) {
        AddSlotDialog(
            onDismiss = onAddSlotDismiss,
            onConfirm = onAddSlotConfirm,
            existingAppointments = appointments
        )
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
                TextButton(onClick = { showCancelConfirmDialog = null }) { Text("Anuluj") }
            }
        )
    }

    showCompleteConfirmDialog?.let { app ->
        AlertDialog(
            onDismissRequest = { showCompleteConfirmDialog = null },
            title = { Text("Oznacz jako zrealizowaną") },
            text = { Text("Czy chcesz oznaczyć tę sesję jako pomyślnie zrealizowaną? Spowoduje to przeniesienie wizyty do historii.") },
            confirmButton = {
                Button(
                    onClick = {
                        onCompleteAppointment(app.appointmentId)
                        showCompleteConfirmDialog = null
                    }
                ) {
                    Text("Zrealizowano", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteConfirmDialog = null }) { Text("Anuluj") }
            }
        )
    }

    showDeleteConfirmDialog?.let { app ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Usuń wolny termin") },
            text = {
                val dateParts = app.date.split("-")
                val displayDate = if (dateParts.size == 3) "${dateParts[2]}.${dateParts[1]}.${dateParts[0]}" else app.date
                Text("Czy na pewno chcesz usunąć wolny termin $displayDate o ${app.time} ze swojego kalendarza?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAppointment(app.appointmentId)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Usuń", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) { Text("Anuluj") }
            }
        )
    }
}