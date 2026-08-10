package com.example.ui.calendar.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AppointmentEntity
import com.example.data.local.entity.UserEntity

@Composable
fun AppointmentCard(
    appointment: AppointmentEntity,
    otherUser: UserEntity?,
    isPsychologist: Boolean,
    onComplete: () -> Unit = {},
    onCancel: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (appointment.status) {
                "FREE" -> MaterialTheme.colorScheme.surface
                "BOOKED" -> if (isPsychologist) MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            when (appointment.status) {
                "BOOKED" -> if (isPsychologist) MaterialTheme.colorScheme.outline.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val dateParts = appointment.date.split("-")
                    val displayDate = if (dateParts.size == 3) "${dateParts[2]}.${dateParts[1]}.${dateParts[0]}" else appointment.date
                    Text(
                        text = "$displayDate o ${appointment.time}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Status badge
                Box(
                    modifier = Modifier
                        .background(
                            color = when (appointment.status) {
                                "FREE" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                "BOOKED" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                                else -> Color.Gray.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (appointment.status) {
                            "FREE" -> "Wolny"
                            "BOOKED" -> if (isPsychologist) "Zarezerwowany" else "Potwierdzona"
                            "COMPLETED" -> if (isPsychologist) "Ukończony" else "Zrealizowano"
                            else -> appointment.status
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (appointment.status) {
                            "FREE" -> MaterialTheme.colorScheme.primary
                            "BOOKED" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (appointment.status == "BOOKED") {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(10.dp))
                
                if (otherUser != null) {
                    Text(
                        text = if (isPsychologist) "Pacjent: ${otherUser.firstName} ${otherUser.lastName}" else "Psycholog: ${otherUser.firstName} ${otherUser.lastName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (!isPsychologist && otherUser.specializations.isNotEmpty()) {
                        Text(
                            text = "Specjalizacje: ${otherUser.specializations}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    Text(
                        text = if (isPsychologist) "Pacjent (użytkownik id: ${appointment.patientId})" else "Psycholog (użytkownik id: ${appointment.psychologistId})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (appointment.notes.isNotEmpty()) {
                    Text(
                        text = if (isPsychologist) "Opis problemu: ${appointment.notes}" else "Twój opis: ${appointment.notes}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                if (isPsychologist) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onComplete,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Zakończ", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Odwołaj", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Odwołaj rezerwację", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (appointment.status == "COMPLETED") {
                Spacer(modifier = Modifier.height(if (isPsychologist) 6.dp else 0.dp))
                if (isPsychologist) {
                    Text(
                        text = if (otherUser != null) "Pacjent: ${otherUser.firstName} ${otherUser.lastName} (Zakończona wizyta)" else "Zakończona wizyta",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    // Patient history view is a bit different in original code, handled here for consistency
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (otherUser != null) "Psycholog: ${otherUser.firstName} ${otherUser.lastName}" else "Ukończona sesja",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (appointment.status == "FREE" && isPsychologist) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Usuń wolny slot",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}
