package com.example.stellare.ui.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.ui.auth.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    // Celowo remember, nie rememberSaveable: stan zapisany trafia na dysk, a hasło nigdy nie może tam wylądować.
    var password by remember { mutableStateOf("") }
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    var showPasswordReset by rememberSaveable { mutableStateOf(false) }

    if (showPasswordReset) {
        PasswordResetDialog(
            viewModel = viewModel,
            initialEmail = email,
            onDismiss = { showPasswordReset = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cofnij")
            }
            Text("Logowanie", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }

        Spacer(modifier = Modifier.height(48.dp))

        if (authError != null) {
            Text(
                text = authError ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; viewModel.clearAuthError() },
            label = { Text("E-mail") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; viewModel.clearAuthError() },
            label = { Text("Hasło") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            shape = RoundedCornerShape(12.dp)
        )

        TextButton(
            onClick = { showPasswordReset = true },
            modifier = Modifier.align(Alignment.End).padding(bottom = 16.dp)
        ) {
            Text("Nie pamiętasz hasła?")
        }

        Button(
            onClick = { viewModel.login(email, password) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Zaloguj się", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        SocialAuthButtons(
            viewModel = viewModel
        )
    }
}
