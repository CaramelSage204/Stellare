package com.example.stellare.ui.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.theme.stellareButtonColors

@Composable
fun RegistrationScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    val authError by viewModel.authError.collectAsStateWithLifecycle()

    var role by rememberSaveable { mutableStateOf(UserRole.PATIENT) }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var ageString by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("Kobieta") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Celowo remember, nie rememberSaveable: stan zapisany trafia na dysk, a hasło nigdy nie może tam wylądować.
    var password by remember { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }

    var priceText by rememberSaveable { mutableStateOf("") }
    val totalSteps = RegistrationRules.totalSteps(role)
    val priceError = if (RegistrationRules.isProfessional(role)) RegistrationRules.priceError(priceText) else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (step > 1) step-- else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cofnij")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = RegistrationRules.stepLabel(step, role),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LinearProgressIndicator(
            progress = { RegistrationRules.progress(step, role) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        if (authError != null) {
            Text(
                text = authError ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        }

        when (step) {
            1 -> RegistrationRoleStep(role = role, onRoleChange = { role = it })
            2 -> RegistrationIdentityStep(
                role = role,
                firstName = firstName,
                onFirstNameChange = { firstName = it },
                lastName = lastName,
                onLastNameChange = { lastName = it },
                ageString = ageString,
                onAgeChange = { ageString = it },
                gender = gender,
                onGenderChange = { gender = it },
                bio = bio,
                onBioChange = { bio = it }
            )
            3 -> RegistrationPhoneStep(
                phone = phone,
                onPhoneChange = { phone = it },
                onSkip = {
                    phone = ""
                    step++
                }
            )
            4 -> RegistrationCredentialsStep(
                email = email,
                onEmailChange = { email = it },
                password = password,
                onPasswordChange = { password = it }
            )
            5 -> RegistrationPriceStep(
                priceText = priceText,
                onPriceChange = { priceText = it },
                priceError = priceError
            )
        }


        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        val nextBlockMessage = RegistrationRules.stepBlockMessage(step, role, firstName, lastName, ageString, phone, priceText)
        val nextBlocked = nextBlockMessage != null
        if (nextBlockMessage != null) {
            Text(
                nextBlockMessage,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }
        Button(
            onClick = {
                if (step < totalSteps) {
                    step++
                } else {
                    if (email.isBlank() || password.isBlank()) return@Button

                    viewModel.register(
                        firstName = firstName,
                        lastName = lastName,
                        age = RegistrationRules.parseAge(ageString) ?: return@Button,
                        gender = gender,
                        phone = phone,
                        email = email,
                        password = password,
                        role = role,
                        qualifications = "",
                        specializations = "",
                        pricePerSession = if (RegistrationRules.isProfessional(role)) RegistrationRules.parsePrice(priceText) ?: return@Button else 0.0,
                        bio = bio
                    )
                }
            },
            enabled = !nextBlocked,
            shape = RoundedCornerShape(12.dp),
            colors = stellareButtonColors(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Text(
                if (step == totalSteps) "Zakończ i Zapisz" else "Dalej",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
