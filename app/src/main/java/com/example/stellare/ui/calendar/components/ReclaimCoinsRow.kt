package com.example.stellare.ui.calendar.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentSchedule
import com.example.stellare.ui.calendar.DepositState
import com.example.stellare.ui.calendar.ReclaimOffer
import com.example.stellare.ui.theme.stellareButtonColors

@Composable
fun ReclaimCoinsRow(
    appointment: AppointmentModel,
    participantName: String,
    deposit: DepositState,
    userId: String,
    nowMillis: Long,
    onReclaim: () -> Unit
) {
    val amount = ReclaimOffer.amount(appointment, deposit, userId, nowMillis) ?: return
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Wizyta u: $participantName, ${AppointmentSchedule.displayDate(appointment.date)} ${appointment.time}",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = "Od terminu minęło ponad 14 dni, a wizyta nie została rozliczona. Możesz odzyskać $amount monet z depozytu.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Button(
                onClick = onReclaim,
                modifier = Modifier.fillMaxWidth(),
                colors = stellareButtonColors()
            ) {
                Text("Odzyskaj $amount monet", fontWeight = FontWeight.Bold)
            }
        }
    }
}
