package com.example.stellare.ui.psychologistdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stellare.ui.psychologistdetail.BookingActionsLayout

@Composable
fun BookingActions(
    canBook: Boolean,
    onStartChat: () -> Unit,
    onBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stacked = BookingActionsLayout.stacked(LocalDensity.current.fontScale, canBook)
    if (stacked) {
        Column(
            modifier = modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChatButton(onStartChat, Modifier.fillMaxWidth())
            BookButton(onBook, Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChatButton(onStartChat, Modifier.weight(1.5f))
            if (canBook) BookButton(onBook, Modifier.weight(1.5f))
        }
    }
}

@Composable
private fun ChatButton(onClick: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(Icons.Default.Chat, contentDescription = "Czat")
        Spacer(modifier = Modifier.width(8.dp))
        Text("Rozpocznij czat", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BookButton(onClick: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(Icons.Default.Check, contentDescription = "Zarezerwuj")
        Spacer(modifier = Modifier.width(6.dp))
        Text("Rezerwuj termin", fontWeight = FontWeight.Bold)
    }
}
