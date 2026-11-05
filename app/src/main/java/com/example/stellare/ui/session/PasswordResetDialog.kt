package com.example.stellare.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.ui.components.dialogTextScroll
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.auth.PasswordResetState
import com.example.stellare.ui.theme.stellareButtonColors

@Composable
fun PasswordResetDialog(
    viewModel: AuthViewModel,
    initialEmail: String,
    onDismiss: () -> Unit
) {
    val state by viewModel.passwordReset.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    val isSending = state == PasswordResetState.Sending
    val cooldownSeconds by viewModel.resetCooldownSeconds.collectAsStateWithLifecycle()
    val isSent = state is PasswordResetState.Sent

    val dismiss = {
        viewModel.resetPasswordResetState()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Reset hasła") },
        text = {
            Column(modifier = Modifier.dialogTextScroll()) {
                Text(
                    "Podaj adres e-mail konta. Wyślemy na niego link do ustawienia nowego hasła.",
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        viewModel.resetPasswordResetState()
                    },
                    label = { Text("E-mail") },
                    singleLine = true,
                    enabled = !isSending,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                when (val current = state) {
                    is PasswordResetState.Sent -> Text(
                        current.message,
                        modifier = Modifier.padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    is PasswordResetState.Failed -> Text(
                        current.message,
                        modifier = Modifier.padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Unit
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.sendPasswordReset(email) },
                enabled = !isSending && cooldownSeconds == 0 && email.isNotBlank(),
                colors = stellareButtonColors()
            ) {
                Text(
                    when {
                        isSending -> "Wysyłanie..."
                        cooldownSeconds > 0 -> "Wyślij ponownie za $cooldownSeconds s"
                        isSent -> "Wyślij ponownie"
                        else -> "Wyślij link"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) {
                Text(if (isSent) "Zamknij" else "Anuluj")
            }
        }
    )
}