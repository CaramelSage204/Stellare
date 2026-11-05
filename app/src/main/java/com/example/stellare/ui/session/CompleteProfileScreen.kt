package com.example.stellare.ui.session

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.ui.components.dialogTextScroll
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.theme.stellareButtonColors
import com.example.stellare.ui.theme.stellareTextButtonColors

private val roleOptions = listOf(
    UserRole.PATIENT to "Pacjent",
    UserRole.PSYCHOLOGIST to "Dyplomowany Psycholog",
    UserRole.PSYCHOLOGY_STUDENT to "Student Psychologii"
)

private val genderOptions = listOf("Kobieta", "Mężczyzna")

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun CompleteProfileScreen(viewModel: AuthViewModel) {
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSavingProfile.collectAsStateWithLifecycle()

    var role by rememberSaveable { mutableStateOf(UserRole.PATIENT) }
    val suggestedName = remember { viewModel.suggestedName() }
    var firstName by rememberSaveable { mutableStateOf(suggestedName.first) }
    var lastName by rememberSaveable { mutableStateOf(suggestedName.second) }
    var ageText by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf(genderOptions.first()) }
    var bio by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.clearAuthError() }

    val activity = LocalActivity.current
    val initialDraft = remember { ProfileDraft.initial(suggestedName, genderOptions.first()) }
    val draft = ProfileDraft(role, firstName, lastName, ageText, gender, bio, priceText)
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = ProfileDiscard.needsConfirmation(initialDraft, draft)) { confirmingDiscard = true }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text("Porzucić uzupełnianie profilu?") },
            text = { Text("Wpisane dane zostaną utracone, a aplikacja się zamknie. Przy kolejnym otwarciu zaczniesz od pustego formularza.", modifier = Modifier.dialogTextScroll()) },
            confirmButton = {
                TextButton(onClick = { activity?.finish() }) {
                    Text("Porzuć i zamknij", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDiscard = false }) {
                    Text("Wróć do formularza")
                }
            }
        )
    }

    val professional = RegistrationRules.isProfessional(role)
    val priceError = if (professional) RegistrationRules.priceError(priceText) else null
    val blockMessage = ProfileCompletion.blockMessage(role, firstName, lastName, ageText, priceText)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Uzupełnij profil",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Twoje konto jest już założone, ale nie ma jeszcze profilu. Wybierz rolę i podaj dane, żeby wejść do aplikacji.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        if (authError != null) {
            Text(
                text = authError ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        }

        Text("Kim jesteś w aplikacji Stellare?", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
        Column(modifier = Modifier.selectableGroup()) {
            roleOptions.forEach { (option, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = role == option, role = Role.RadioButton, onClick = { role = option })
                ) {
                    RadioButton(selected = role == option, onClick = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(label, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("Imię") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Nazwisko") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = ageText,
            onValueChange = { ageText = it },
            label = { Text("Wiek") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp)
        )

        Text("Płeć", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
        FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            genderOptions.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.selectable(selected = gender == option, role = Role.RadioButton, onClick = { gender = option })
                ) {
                    RadioButton(selected = gender == option, onClick = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(option, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text(if (professional) "Twój krótki biogram / doświadczenie" else "Czego poszukujesz / Twoje obawy") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            shape = RoundedCornerShape(12.dp)
        )

        if (professional) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Cena sesji (WKT)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = priceText.isNotBlank() && priceError != null,
                supportingText = {
                    Text("Bez dodatniej ceny nie wystawisz terminów w kalendarzu. Możesz ją później zmienić w profilu.")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (blockMessage != null) {
            Text(
                text = blockMessage,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }
        Button(
            onClick = { viewModel.completeProfile(role, firstName, lastName, ageText, gender, bio, priceText) },
            enabled = blockMessage == null && !isSaving,
            shape = RoundedCornerShape(12.dp),
            colors = stellareButtonColors(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Text(if (isSaving) "Zapisywanie..." else "Zapisz i wejdź", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = viewModel::logout,
            enabled = !isSaving,
            colors = stellareTextButtonColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Wyloguj się", fontWeight = FontWeight.Bold)
        }
    }
}
