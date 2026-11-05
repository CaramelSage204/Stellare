package com.example.stellare.ui.psychologistdetail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.ui.components.dialogTextScroll
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.ui.calendar.BookingState
import com.example.stellare.ui.psychologistdetail.bookingDateLabel
import com.example.stellare.ui.psychologistdetail.canAffordBooking
import com.example.stellare.ui.theme.coinGold
import com.example.stellare.ui.theme.stellareButtonColors

@Composable
fun BookingConfirmDialog(
    psychologistName: String,
    appointment: AppointmentModel,
    balance: Int,
    notes: String,
    bookingState: BookingState,
    onNotesChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onTopUp: () -> Unit,
    onDismiss: () -> Unit
) {
    val cost = appointment.priceCoins
    val canAfford = canAffordBooking(balance, cost)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Potwierdź rezerwację") },
        text = {
            Column(modifier = Modifier.dialogTextScroll()) {
                Text("Rezerwujesz sesję u psychologa: $psychologistName", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Termin: ${bookingDateLabel(appointment.date)} o godzinie ${appointment.time}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                CostCard(cost = cost, balance = balance, canAfford = canAfford)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text("Notatka dla psychologa (opcjonalnie)") },
                    placeholder = { Text("Napisz krótko, czego dotyczy problem...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                (bookingState as? BookingState.Error)?.message?.let { message ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                BookingActionsColumn(
                    canAfford = canAfford,
                    cost = cost,
                    inProgress = bookingState is BookingState.InProgress,
                    onConfirm = onConfirm,
                    onTopUp = onTopUp,
                    onDismiss = onDismiss
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun BookingActionsColumn(
    canAfford: Boolean,
    cost: Int,
    inProgress: Boolean,
    onConfirm: () -> Unit,
    onTopUp: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.End) {
        if (canAfford) {
            Button(
                enabled = !inProgress,
                onClick = onConfirm,
                colors = stellareButtonColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Zatwierdź ($cost monet)")
            }
        } else {
            Button(onClick = onTopUp, modifier = Modifier.fillMaxWidth()) {
                Text("Doładuj portfel monet")
            }
        }
        TextButton(onClick = onDismiss) { Text("Anuluj") }
    }
}
@Composable
private fun CostCard(cost: Int, balance: Int, canAfford: Boolean) {
    val accent = MaterialTheme.coinGold
    Card(
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Paid, contentDescription = "Portfel", tint = accent, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Koszt rezerwacji: $cost monet WKT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Twój balans: $balance monet WKT",
                    fontSize = 11.sp,
                    color = if (canAfford) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BookingSuccessDialog(onDismiss: () -> Unit, onOpenCalendar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pomyślna rezerwacja! 🎉") },
        text = {
            Text("Twoja wizyta została pomyślnie zarezerwowana. Znajdziesz ją w zakładce „Kalendarz” na dolnym pasku aplikacji.", modifier = Modifier.dialogTextScroll())
        },
        confirmButton = { Button(onClick = onOpenCalendar) { Text("Przejdź do Kalendarza") } }
    )
}
