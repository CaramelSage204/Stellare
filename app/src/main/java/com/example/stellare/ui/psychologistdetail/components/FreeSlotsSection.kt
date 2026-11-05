package com.example.stellare.ui.psychologistdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.ui.psychologistdetail.SlotOption
import com.example.stellare.ui.psychologistdetail.hasUnpricedSlots
import com.example.stellare.ui.psychologistdetail.slotOption

@Composable
fun FreeSlotsSection(
    slots: List<AppointmentModel>,
    selectedId: String?,
    onSelect: (AppointmentModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle("Rezerwacja sesji online", Modifier.padding(bottom = 4.dp))
        if (slots.isEmpty()) {
            NoSlotsCard()
        } else {
            Text(
                "Wybierz dogodny termin z kalendarza psychologa:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                slots.forEach { appointment ->
                    SlotChip(option = slotOption(appointment, selectedId), onClick = { onSelect(appointment) })
                }
            }
        }
        if (hasUnpricedSlots(slots)) {
            Text(
                "Terminów bez ceny nie da się zarezerwować, bo psycholog nie ustalił za nie opłaty w monetach. Napisz do niego w czacie.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun NoSlotsCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.EventBusy,
                "Brak terminów",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Brak dostępnych wolnych terminów na ten moment.",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Napisz w czacie do psychologa, aby uzgodnić wolny termin.",
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SlotChip(option: SlotOption, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val colors = MaterialTheme.colorScheme
    val background = if (option.isSelected) colors.primary else colors.surfaceVariant
    val borderColor = if (option.isSelected) colors.primary else colors.outline
    val textColor = when {
        option.isSelected -> colors.onPrimary
        option.isBookable -> colors.onSurface
        else -> colors.onSurface.copy(alpha = 0.4f)
    }
    Box(
        modifier = Modifier
            .background(background, shape)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = option.isBookable, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text = option.label, fontWeight = FontWeight.Bold, color = textColor)
    }
}
