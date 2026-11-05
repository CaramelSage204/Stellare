package com.example.stellare.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.auth.PhoneVerificationState
import com.example.stellare.ui.theme.stellareButtonColors

private const val CODE_LENGTH = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneVerificationScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.phoneVerification.collectAsStateWithLifecycle()
    val cooldownSeconds by viewModel.phoneResendCooldownSeconds.collectAsStateWithLifecycle()
    var number by rememberSaveable { mutableStateOf(viewModel.takePhoneVerificationOffer().orEmpty()) }
    var code by remember(state is PhoneVerificationState.EnteringNumber) { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.openPhoneVerification() }

    val leave = {
        viewModel.closePhoneVerification()
        onBack()
    }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Numer telefonu", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrót")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            when (val current = state) {
                is PhoneVerificationState.Verified -> VerifiedStep(
                    state = current,
                    onRetrySave = viewModel::retrySavingPhoneToProfile,
                    onDone = leave
                )
                is PhoneVerificationState.EnteringCode -> {
                    PhoneIntro()
                    CodeStep(
                        phoneNumber = current.phoneNumber,
                        code = code,
                        onCodeChange = { code = it.filter(Char::isDigit).take(CODE_LENGTH) },
                        error = current.error,
                        checking = false,
                        cooldownSeconds = cooldownSeconds,
                        onConfirm = { viewModel.confirmPhoneCode(code) },
                        onResend = { viewModel.requestPhoneCode(current.phoneNumber) },
                        onChangeNumber = viewModel::changePhoneNumber
                    )
                }
                is PhoneVerificationState.CheckingCode -> {
                    PhoneIntro()
                    CodeStep(
                        phoneNumber = current.phoneNumber,
                        code = code,
                        onCodeChange = {},
                        error = null,
                        checking = true,
                        cooldownSeconds = cooldownSeconds,
                        onConfirm = {},
                        onResend = {},
                        onChangeNumber = {}
                    )
                }
                is PhoneVerificationState.EnteringNumber -> {
                    PhoneIntro()
                    NumberStep(
                        number = number,
                        onNumberChange = { number = it },
                        error = current.error,
                        sending = false,
                        cooldownSeconds = cooldownSeconds,
                        onSend = { viewModel.requestPhoneCode(number) }
                    )
                }
                PhoneVerificationState.SendingCode -> {
                    PhoneIntro()
                    NumberStep(
                        number = number,
                        onNumberChange = {},
                        error = null,
                        sending = true,
                        cooldownSeconds = cooldownSeconds,
                        onSend = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneIntro() {
    Text(
        "Potwierdzenie numeru jest opcjonalne. Wyślemy SMS z kodem na podany numer. Dopiero po wpisaniu kodu numer zostanie powiązany z Twoim kontem i zapisany w profilu, do którego dostęp mają inni zalogowani użytkownicy.",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 24.dp)
    )
}

@Composable
private fun NumberStep(
    number: String,
    onNumberChange: (String) -> Unit,
    error: String?,
    sending: Boolean,
    cooldownSeconds: Int,
    onSend: () -> Unit
) {
    OutlinedTextField(
        value = number,
        onValueChange = onNumberChange,
        label = { Text("Numer telefonu (np. +48 501 234 567)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        singleLine = true,
        enabled = !sending,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = RoundedCornerShape(12.dp)
    )
    Button(
        onClick = onSend,
        enabled = number.isNotBlank() && !sending && cooldownSeconds == 0,
        shape = RoundedCornerShape(12.dp),
        colors = stellareButtonColors(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        Text(
            when {
                sending -> "Wysyłanie..."
                cooldownSeconds > 0 -> "Wyślij ponownie za $cooldownSeconds s"
                else -> "Wyślij kod"
            },
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CodeStep(
    phoneNumber: String,
    code: String,
    onCodeChange: (String) -> Unit,
    error: String?,
    checking: Boolean,
    cooldownSeconds: Int,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onChangeNumber: () -> Unit
) {
    Text(
        "Wysłaliśmy SMS z kodem na numer $phoneNumber. Wpisz $CODE_LENGTH cyfr z wiadomości.",
        modifier = Modifier.padding(bottom = 16.dp)
    )
    OutlinedTextField(
        value = code,
        onValueChange = onCodeChange,
        label = { Text("Kod z SMS-a") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        singleLine = true,
        enabled = !checking,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = RoundedCornerShape(12.dp)
    )
    Button(
        onClick = onConfirm,
        enabled = code.length == CODE_LENGTH && !checking,
        shape = RoundedCornerShape(12.dp),
        colors = stellareButtonColors(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        Text(if (checking) "Sprawdzanie..." else "Potwierdź numer", fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onResend,
        enabled = !checking && cooldownSeconds == 0,
        shape = RoundedCornerShape(12.dp),
        colors = stellareButtonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Text(if (cooldownSeconds > 0) "Wyślij ponownie za $cooldownSeconds s" else "Wyślij kod ponownie")
    }
    if (!checking) {
        TextButton(onClick = onChangeNumber, modifier = Modifier.fillMaxWidth()) {
            Text("Zmień numer", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VerifiedStep(
    state: PhoneVerificationState.Verified,
    onRetrySave: () -> Unit,
    onDone: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "Numer ${state.phoneNumber} jest potwierdzony na Twoim koncie.",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
    if (state.profileSaveFailed) {
        Text(
            "Nie udało się zapisać numeru w profilu. Konto go zna, ale profil jeszcze nie.",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(
            onClick = onRetrySave,
            shape = RoundedCornerShape(12.dp),
            colors = stellareButtonColors(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Text("Zapisz numer w profilu", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
    } else {
        Text(
            "Numer zapisaliśmy w Twoim profilu. Dane profilu są dostępne dla innych zalogowanych użytkowników.",
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
    Button(
        onClick = onDone,
        shape = RoundedCornerShape(12.dp),
        colors = stellareButtonColors(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        Text("Gotowe", fontWeight = FontWeight.Bold)
    }
}
