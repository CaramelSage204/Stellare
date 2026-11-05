package com.example.stellare.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.ui.components.dialogTextScroll
import com.example.stellare.ui.theme.stellareOutlinedButtonBorder

@Composable
fun TutorialPromptDialog(onStart: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Czy chcesz przejść szybki samouczek?") },
        text = { Text("Dziękujemy za rejestrację w Stellare! Chcesz dowiedzieć się, jak otwierać swoje konto, pisać oferty, znajdować psychologów lub pacjentów, pisać notatki terapeutyczne oraz ustalić stawkę?", modifier = Modifier.dialogTextScroll()) },
        confirmButton = {
            Button(
                onClick = onStart
            ) {
                Text("Tak, jasne!", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onSkip,
                border = stellareOutlinedButtonBorder()
            ) {
                Text("Pomiń", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}

@Composable
fun PhoneVerificationOfferDialog(phone: String, onConfirm: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Potwierdzić numer telefonu?") },
        text = { Text("W rejestracji podano numer $phone. Wyślemy na niego SMS z kodem, żeby powiązać numer z kontem. Możesz to zrobić też później w menu profilu, w pozycji Numer telefonu.", modifier = Modifier.dialogTextScroll()) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Potwierdź numer", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onLater, border = stellareOutlinedButtonBorder()) {
                Text("Później", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}

@Composable
fun NotificationsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Centrum Powiadomień", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().dialogTextScroll(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsNone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Brak powiadomień", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Aplikacja nie wysyła powiadomień. Nowe wiadomości, rezerwacje i odpowiedzi znajdziesz w samej aplikacji.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Zamknij")
            }
        }
    )
}
