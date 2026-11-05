package com.example.stellare.ui.calendar.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.stellare.ui.components.dialogTextScroll
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentSchedule
import com.example.stellare.ui.calendar.AxisAppointments
import com.example.stellare.ui.calendar.ConfirmationTarget
import com.example.stellare.ui.calendar.ConfirmationTarget.Operation
import com.example.stellare.ui.calendar.DayAgenda
import com.example.stellare.ui.calendar.DepositState
import com.example.stellare.ui.calendar.RefundMessages
import com.example.stellare.ui.calendar.ReclaimOffer
import com.example.stellare.ui.calendar.SessionLabels
import com.example.stellare.ui.calendar.components.DayNavigator
import com.example.stellare.ui.calendar.components.HourAxis
import com.example.stellare.ui.calendar.components.ReclaimCoinsRow
import com.example.stellare.ui.calendar.components.SessionDetailDialog
import com.example.stellare.ui.calendar.rememberDeposit
import com.example.stellare.ui.calendar.rememberNowMillis
import kotlinx.coroutines.flow.Flow
import java.util.TimeZone

@Composable
fun PatientCalendarView(
    appointments: List<AppointmentModel>,
    userId: String?,
    participantNames: Map<String, String>,
    selectedDayMillis: Long,
    todayMillis: Long,
    zone: TimeZone,
    nowProvider: () -> Long,
    onSelectDay: (Long) -> Unit,
    depositOf: (String) -> Flow<DepositState>,
    onCancelAppointment: (String) -> Unit,
    onReclaimAppointment: (String) -> Unit
) {
    var detailId by remember { mutableStateOf<String?>(null) }
    var cancelConfirmId by remember { mutableStateOf<String?>(null) }

    val nowMillis = rememberNowMillis(nowProvider, appointments)
    val onAxis = remember(appointments, userId) { AxisAppointments.forAxis(appointments, false, userId) }
    val agenda = remember(onAxis, selectedDayMillis, zone) { DayAgenda.of(onAxis, selectedDayMillis, zone) }

    val reclaimable = remember(appointments, userId, nowMillis) { ReclaimOffer.candidates(appointments, userId, nowMillis) }

    Column(modifier = Modifier.fillMaxSize()) {
        DayNavigator(selectedDayMillis, todayMillis, zone, onSelectDay)
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            reclaimable.forEach { appointment ->
                key(appointment.appointmentId) {
                    ReclaimCoinsRow(
                        appointment = appointment,
                        participantName = SessionLabels.participantName(appointment, false, participantNames),
                        deposit = rememberDeposit(depositOf, appointment.appointmentId),
                        userId = userId.orEmpty(),
                        nowMillis = nowMillis,
                        onReclaim = { onReclaimAppointment(appointment.appointmentId) }
                    )
                }
            }
            HourAxis(
                agenda = agenda,
                dayMillis = selectedDayMillis,
                zone = zone,
                titleOf = { SessionLabels.title(it, false, participantNames) },
                onSelect = { detailId = it.appointmentId }
            )
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    onAxis.firstOrNull { it.appointmentId == detailId }?.let { appointment ->
        SessionDetailDialog(
            appointment = appointment,
            participantName = SessionLabels.participantName(appointment, false, participantNames),
            isPsychologist = false,
            nowMillis = nowMillis,
            zone = zone,
            missedVisitRefund = null,
            onDismiss = { detailId = null },
            onComplete = {},
            onCancel = {
                cancelConfirmId = appointment.appointmentId
                detailId = null
            },
            onDelete = {}
        )
    }

    ConfirmationTarget.resolve(cancelConfirmId, onAxis, Operation.CANCEL)?.let { app ->
        val deposit = rememberDeposit(depositOf, app.appointmentId)
        AlertDialog(
            onDismissRequest = { cancelConfirmId = null },
            title = { Text("Potwierdź odwołanie wizyty") },
            text = {
                val displayDate = AppointmentSchedule.displayDate(app.date)
                Text(
                    text = "Czy na pewno chcesz odwołać wizytę zaplanowaną na $displayDate o godzinie ${app.time}?\n\n" +
                            RefundMessages.beforeCancel(app, deposit, viewerIsPsychologist = false, nowMillis = nowMillis),
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
                    Text("Odwołaj wizytę", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelConfirmId = null }) { Text("Anuluj") }
            }
        )
    }
}
