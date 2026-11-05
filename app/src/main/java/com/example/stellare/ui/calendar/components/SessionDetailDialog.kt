package com.example.stellare.ui.calendar.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.ui.components.dialogTextScroll
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentSchedule
import com.example.stellare.data.model.AppointmentTiming
import com.example.stellare.data.model.CompletionEligibility
import com.example.stellare.ui.calendar.SessionLabels
import java.util.TimeZone
import com.example.stellare.ui.theme.stellareButtonColors

@Composable
fun SessionDetailDialog(
    appointment: AppointmentModel,
    participantName: String,
    isPsychologist: Boolean,
    nowMillis: Long,
    zone: TimeZone,
    missedVisitRefund: Int?,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val canCancel = AppointmentTiming.canCancel(appointment, nowMillis, isPsychologist)
    val isFree = appointment.status == "FREE"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isFree) SessionLabels.FREE_TITLE else participantName) },
        text = {
            Column(modifier = Modifier.dialogTextScroll(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${AppointmentSchedule.displayDate(appointment.date)} · ${SessionLabels.timeRange(appointment, zone)}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = SessionLabels.statusLabel(appointment, isPsychologist, canCancel),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (appointment.notes.isNotEmpty()) {
                    Text(
                        text = if (isPsychologist) "Opis problemu: ${appointment.notes}" else "Twój opis: ${appointment.notes}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (appointment.status == "BOOKED" && isPsychologist) {
                    PsychologistBookedActions(appointment, nowMillis, canCancel, missedVisitRefund, onComplete, onCancel)
                }
                if (appointment.status == "BOOKED" && !isPsychologist && canCancel) {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Odwołaj rezerwację", color = MaterialTheme.colorScheme.onError)
                    }
                }
                if (isFree && isPsychologist) {
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Usuń wolny termin", color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Zamknij") }
        }
    )
}

@Composable
private fun PsychologistBookedActions(
    appointment: AppointmentModel,
    nowMillis: Long,
    canCancel: Boolean,
    missedVisitRefund: Int?,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val eligibility = CompletionEligibility.of(appointment, nowMillis)
    if (!eligibility.isAllowed) {
        Text(
            text = "Wizytę można zakończyć dopiero po jej rozpoczęciu (${AppointmentSchedule.displayDate(appointment.date)}, ${appointment.time}).",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (missedVisitRefund != null) {
        Text(
            text = "Termin tej wizyty minął. Jeśli wizyta się nie odbyła, zwróć pacjentowi $missedVisitRefund monet.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Button(
        onClick = onComplete,
        enabled = eligibility.isAllowed,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        colors = stellareButtonColors()
    ) {
        Text("Zakończ", fontWeight = FontWeight.Bold)
    }
    if (canCancel) {
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text(
                text = if (missedVisitRefund != null) "Zwróć monety" else "Odwołaj",
                color = MaterialTheme.colorScheme.onError,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
