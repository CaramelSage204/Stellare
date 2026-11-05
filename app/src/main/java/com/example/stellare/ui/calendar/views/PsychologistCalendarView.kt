package com.example.stellare.ui.calendar.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.ui.components.dialogTextScroll
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentOrder
import com.example.stellare.data.model.AppointmentSchedule
import com.example.stellare.ui.calendar.AxisAppointments
import com.example.stellare.ui.calendar.ConfirmationTarget
import com.example.stellare.ui.calendar.ConfirmationTarget.Operation
import com.example.stellare.ui.calendar.DayAgenda
import com.example.stellare.ui.calendar.DepositState
import com.example.stellare.ui.calendar.RefundMessages
import com.example.stellare.ui.calendar.SessionLabels
import com.example.stellare.ui.calendar.components.AddSlotDialog
import com.example.stellare.ui.calendar.components.DayNavigator
import com.example.stellare.ui.calendar.components.HourAxis
import com.example.stellare.ui.calendar.components.SessionDetailDialog
import com.example.stellare.ui.calendar.components.StatBox
import com.example.stellare.ui.calendar.rememberDeposit
import com.example.stellare.ui.calendar.rememberNowMillis
import kotlinx.coroutines.flow.Flow
import java.util.TimeZone

@Composable
fun PsychologistCalendarView(
    appointments: List<AppointmentModel>,
    userId: String?,
    participantNames: Map<String, String>,
    selectedDayMillis: Long,
    todayMillis: Long,
    zone: TimeZone,
    nowProvider: () -> Long,
    showAddSlotDialog: Boolean,
    onSelectDay: (Long) -> Unit,
    onAddSlotDismiss: () -> Unit,
    onAddSlotConfirm: (Long) -> Unit,
    depositOf: (String) -> Flow<DepositState>,
    onCompleteAppointment: (String) -> Unit,
    onCancelAppointment: (String) -> Unit,
    onDeleteAppointment: (String) -> Unit
) {
    val nowMillis = rememberNowMillis(nowProvider, appointments)
    var detailId by remember { mutableStateOf<String?>(null) }
    var cancelConfirmId by remember { mutableStateOf<String?>(null) }
    var completeConfirmId by remember { mutableStateOf<String?>(null) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    val onAxis = remember(appointments) { AxisAppointments.forAxis(appointments, true, userId) }
    val agenda = remember(onAxis, selectedDayMillis, zone) { DayAgenda.of(onAxis, selectedDayMillis, zone) }

    val bookedSlots = appointments.count { it.status == "BOOKED" }
    val completedSlots = appointments.count { it.status == "COMPLETED" }
    val freeSlots = AppointmentOrder.freeUpcomingCount(appointments, nowMillis)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DayNavigator(selectedDayMillis, todayMillis, zone, onSelectDay)
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (appointments.isEmpty()) {
                Text(
                    text = "Twój kalendarz jest pusty. Kliknij przycisk +, aby dodać pierwsze wolne godziny.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HourAxis(
                agenda = agenda,
                dayMillis = selectedDayMillis,
                zone = zone,
                titleOf = { SessionLabels.title(it, true, participantNames) },
                onSelect = { detailId = it.appointmentId }
            )
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    onAxis.firstOrNull { it.appointmentId == detailId }?.let { appointment ->
        val deposit = rememberDeposit(depositOf, appointment.appointmentId)
        SessionDetailDialog(
            appointment = appointment,
            participantName = SessionLabels.participantName(appointment, true, participantNames),
            isPsychologist = true,
            nowMillis = nowMillis,
            zone = zone,
            missedVisitRefund = deposit.missedVisitRefund(appointment, nowMillis),
            onDismiss = { detailId = null },
            onComplete = {
                completeConfirmId = appointment.appointmentId
                detailId = null
            },
            onCancel = {
                cancelConfirmId = appointment.appointmentId
                detailId = null
            },
            onDelete = {
                deleteConfirmId = appointment.appointmentId
                detailId = null
            }
        )
    }

    if (showAddSlotDialog) {
        AddSlotDialog(
            onDismiss = onAddSlotDismiss,
            onConfirm = onAddSlotConfirm,
            existingAppointments = appointments
        )
    }

    ConfirmationTarget.resolve(cancelConfirmId, onAxis, Operation.CANCEL)?.let { app ->
        val deposit = rememberDeposit(depositOf, app.appointmentId)
        val missedRefund = deposit.missedVisitRefund(app, nowMillis)
        val missed = missedRefund != null
        AlertDialog(
            onDismissRequest = { cancelConfirmId = null },
            title = { Text(if (missed) "Zwrot za wizytę, która się nie odbyła" else "Potwierdź odwołanie wizyty") },
            text = {
                val displayDate = AppointmentSchedule.displayDate(app.date)
                val intro = if (missed) {
                    "Termin $displayDate o godzinie ${app.time} już minął, a wizyta nie została zakończona. " +
                        "Zwróć monety tylko wtedy, gdy wizyta się nie odbyła."
                } else {
                    "Czy na pewno chcesz odwołać wizytę zaplanowaną na $displayDate o godzinie ${app.time}?"
                }
                Text(
                    text = intro + "\n\n" + RefundMessages.beforeCancel(app, deposit, viewerIsPsychologist = true, nowMillis = nowMillis),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.dialogTextScroll()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelAppointment(app.appointmentId)
                        cancelConfirmId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        if (missed) "Zwróć $missedRefund monet" else "Odwołaj wizytę",
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelConfirmId = null }) { Text("Anuluj") }
            }
        )
    }

    ConfirmationTarget.resolve(completeConfirmId, onAxis, Operation.COMPLETE)?.let { app ->
        val deposit = rememberDeposit(depositOf, app.appointmentId)
        AlertDialog(
            onDismissRequest = { completeConfirmId = null },
            title = { Text("Oznacz jako zrealizowaną") },
            text = {
                Text(
                    text = "Czy chcesz oznaczyć tę sesję jako pomyślnie zrealizowaną? Spowoduje to przeniesienie wizyty do historii.\n\n" +
                        RefundMessages.beforeComplete(app, deposit, nowMillis),
                    modifier = Modifier.dialogTextScroll()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCompleteAppointment(app.appointmentId)
                        completeConfirmId = null
                    }
                ) {
                    Text("Zrealizowano", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { completeConfirmId = null }) { Text("Anuluj") }
            }
        )
    }

    ConfirmationTarget.resolve(deleteConfirmId, onAxis, Operation.DELETE)?.let { app ->
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("Usuń wolny termin") },
            text = {
                val displayDate = AppointmentSchedule.displayDate(app.date)
                Text("Czy na pewno chcesz usunąć wolny termin $displayDate o ${app.time} ze swojego kalendarza?", modifier = Modifier.dialogTextScroll())
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAppointment(app.appointmentId)
                        deleteConfirmId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Usuń", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) { Text("Anuluj") }
            }
        )
    }
}