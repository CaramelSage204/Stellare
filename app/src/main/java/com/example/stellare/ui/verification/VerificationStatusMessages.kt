package com.example.stellare.ui.verification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StatusMessage(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    content: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        content()
    }
}

@Composable
internal fun PendingStatus() {
    StatusMessage(
        icon = Icons.Filled.HourglassTop,
        iconTint = MaterialTheme.colorScheme.primary,
        title = "Zgłoszenie oczekuje na rozpatrzenie",
        description = "Administrator sprawdzi przesłany dokument najszybciej, jak to możliwe."
    )
}

@Composable
internal fun ApprovedStatus() {
    StatusMessage(
        icon = Icons.Filled.CheckCircle,
        iconTint = MaterialTheme.colorScheme.primary,
        title = "Kwalifikacje zweryfikowane",
        description = "Twoje konto ma teraz odznakę zweryfikowanego użytkownika."
    )
}

@Composable
internal fun RejectedStatus(reviewNote: String, onResubmit: () -> Unit) {
    StatusMessage(
        icon = Icons.Filled.Cancel,
        iconTint = MaterialTheme.colorScheme.error,
        title = "Zgłoszenie zostało odrzucone",
        description = if (reviewNote.isNotBlank()) {
            "Powód: $reviewNote"
        } else {
            "Administrator nie podał powodu odrzucenia."
        }
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onResubmit,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Wyślij ponownie", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
    }
}
