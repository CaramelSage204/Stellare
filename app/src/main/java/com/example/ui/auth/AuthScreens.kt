package com.example.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.local.UserRole
import com.example.ui.WektorViewModel
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onSkipToDashboard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Identity with modern title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_app_icon),
                contentDescription = "Logo Wektor",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Wektor",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("onboarding_title")
            )
        }

        Text(
            text = "Kierunek rozwoju psychologicznego",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Cozy counseling illustration from generate_image
        Image(
            painter = painterResource(id = R.drawable.img_psych_illustration),
            contentDescription = "Counseling Illustration",
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Łączymy kwalifikowanych psychologów, ambitnych studentów psychologii szukających praktyki oraz pacjentów poszukujących wsparcia online.",
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Bullet points of advantages matching Polish notes screenshots
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(Icons.Default.VerifiedUser, "Pacjent", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Dla Pacjenta: Szybkie i proste dobieranie specjalisty online pod Twoje kryteria oraz niższe koszty.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row {
                Icon(Icons.Default.School, "Psycholog", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Dla Psychologa i Studenta: Łatwe odnajdywanie klientów, praca całkowicie zdalna, własne godziny i praktyki uniwersyteckie.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onNavigateToRegister,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("register_button")
        ) {
            Text(
                "Utwórz Konto i Rozpocznij",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onNavigateToLogin,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("login_button")
        ) {
            Text(
                "Zaloguj się na istniejące konto",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onSkipToDashboard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Zaloguj jako pacjent testowy (Eksploruj)",
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun RegistrationScreen(
    viewModel: WektorViewModel,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(1) }

    // Forms states
    var role by remember { mutableStateOf(UserRole.PATIENT) } // UserRole enum
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var ageString by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Kobieta") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }

    // Verification states
    var isSmsSent by remember { mutableStateOf(false) }
    var smsCodeInput by remember { mutableStateOf("") }
    var mockSmsCode = "1928"
    var isSmsVerified by remember { mutableStateOf(false) }

    var qualType by remember { mutableStateOf("Magister") } // "Licencjat", "Magister", "Doktorant", "Certyfikat"
    var isDocumentScanned by remember { mutableStateOf(false) }
    var showScannerOverlay by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (step > 1) step-- else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cofnij")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Krok $step z ${if (role == UserRole.PSYCHOLOGIST || role == UserRole.PSYCHOLOGY_STUDENT) 5 else 4}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Stepper Progress Bar
        LinearProgressIndicator(
            progress = { step.toFloat() / if (role == UserRole.PSYCHOLOGIST || role == UserRole.PSYCHOLOGY_STUDENT) 5f else 4f },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        when (step) {
            1 -> {
                // STEP 1: Choose role
                Text("Kim jesteś w aplikacji Wektor?", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Text("Wybierz swoją rolę, aby dostosować funkcjonalności.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Patient Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (role == UserRole.PATIENT) 2.dp else 1.dp,
                                color = if (role == UserRole.PATIENT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .background(
                                color = if (role == UserRole.PATIENT) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { role = UserRole.PATIENT }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, "Pacjent", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pacjent", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Szukam profesjonalnego wsparcia i rozmowy", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        RadioButton(selected = role == UserRole.PATIENT, onClick = { role = UserRole.PATIENT })
                    }

                    // Psychologist Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (role == UserRole.PSYCHOLOGIST) 2.dp else 1.dp,
                                color = if (role == UserRole.PSYCHOLOGIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .background(
                                color = if (role == UserRole.PSYCHOLOGIST) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { role = UserRole.PSYCHOLOGIST }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.School, "Psycholog", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dyplomowany Psycholog", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Chcę oferować sesje, doradzać i wspierać", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        RadioButton(selected = role == UserRole.PSYCHOLOGIST, onClick = { role = UserRole.PSYCHOLOGIST })
                    }

                    // Student Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (role == UserRole.PSYCHOLOGY_STUDENT) 2.dp else 1.dp,
                                color = if (role == UserRole.PSYCHOLOGY_STUDENT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .background(
                                color = if (role == UserRole.PSYCHOLOGY_STUDENT) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { role = UserRole.PSYCHOLOGY_STUDENT }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.School, "Student", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Student Psychologii", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Chcę zdobywać praktykę i doświadczenie", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        RadioButton(selected = role == UserRole.PSYCHOLOGY_STUDENT, onClick = { role = UserRole.PSYCHOLOGY_STUDENT })
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                SocialAuthButtons(onSocialSelected = { provider ->
                    viewModel.loginUser("${provider.lowercase()}@wektor.pl", preferredRole = role)
                })
            }
            2 -> {
                // STEP 2: Basic info
                Text("Podstawowe informacje", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))

                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Imię") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Nazwisko") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = ageString,
                    onValueChange = { ageString = it },
                    label = { Text("Wiek") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Płeć", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    listOf("Kobieta", "Mężczyzna").forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .border(1.dp, if (gender == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .clickable { gender = option }
                                .padding(12.dp)
                        ) {
                            RadioButton(selected = gender == option, onClick = { gender = option })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(option, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text(if (role == UserRole.PSYCHOLOGIST) "Twój krótki biogram / doświadczenie" else "Czego poszukujesz / Twoje obawy") },
                    modifier = Modifier.fillMaxWidth().height(120.dp).padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            3 -> {
                // STEP 3: Phone & SMS verification simulator
                Text("Weryfikacja numeru telefonu", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Text("Do zalogowania i bezpieczeństwa wymagamy weryfikacji SMS.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 24.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Numer telefonu (np. +48 501 234 567)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSmsVerified
                )

                if (!isSmsSent && !isSmsVerified) {
                    Button(
                        onClick = { isSmsSent = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send code")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wyślij kod SMS", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }

                if (isSmsSent && !isSmsVerified) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Symulacja SMS: Otrzymany kod to: $mockSmsCode", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    OutlinedTextField(
                        value = smsCodeInput,
                        onValueChange = { smsCodeInput = it },
                        label = { Text("Wpisz 4-cyfrowy kod SMS") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            if (smsCodeInput == mockSmsCode) {
                                isSmsVerified = true
                                isSmsSent = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Zweryfikuj kod", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }

                if (isSmsVerified) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, "Sukces", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Telefon został pomyślnie zweryfikowany!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            4 -> {
                // STEP 4: Email & Password
                Text("Dane logowania i 2FA przez pocztę", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Adres E-mail") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Hasło dwuetapowe") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, "2FA", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Wektor wykorzystuje automatyczną dwuetapową autoryzację przez pocztę e-mail, gwarantując pacjentom 100% anonimowości i bezpieczeństwa.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            5 -> {
                // STEP 5: Qualification verification for Psychologist
                Text("Weryfikacja kwalifikacji", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Text("Jako psycholog możesz przejść weryfikację dyplomu, by otrzymać odznakę zaufania na swoim profilu.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 24.dp))

                Text("Twój stopień / Kwalifikacja", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
                listOf("Licencjat", "Magister", "Doktorant", "Certyfikat zawodowy").forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .border(1.dp, if (qualType == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .clickable { qualType = option }
                            .padding(14.dp)
                    ) {
                        RadioButton(selected = qualType == option, onClick = { qualType = option })
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(option, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isDocumentScanned) {
                    Button(
                        onClick = { showScannerOverlay = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DocumentScanner, "Scan", tint = Color.Black)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Zeskanuj dowód i dyplom", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Verified, "Sukces", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Zweryfikowano pomyślnie!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Twój dyplom został przeskanowany automatycznie.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        // Document Scanner Simulator Dialog overlay
        if (showScannerOverlay) {
            Dialog(onDismissRequest = { showScannerOverlay = false }) {
                var scanProgress by remember { mutableStateOf(0f) }
                var scanStage by remember { mutableStateOf("Skanowanie dowodu tożsamości...") }

                LaunchedEffect(Unit) {
                    // Step 1: Scan ID
                    while (scanProgress < 1f) {
                        scanProgress += 0.05f
                        delay(100)
                    }
                    delay(500)
                    scanProgress = 0f
                    scanStage = "Skanowanie dyplomu uczelni..."
                    // Step 2: Scan Diploma
                    while (scanProgress < 1f) {
                        scanProgress += 0.05f
                        delay(100)
                    }
                    delay(500)
                    isDocumentScanned = true
                    showScannerOverlay = false
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Skaner dokumentów Wektor", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))

                        // Box showing scanning visual simulation
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Animated scan line
                            val scanY by animateFloatAsState(
                                targetValue = if (scanProgress > 0.5f) 160f else 20f,
                                label = "ScanLineAnimation"
                            )
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawLine(
                                    color = Color(0xFFF59E0B),
                                    start = Offset(10f, scanY * density),
                                    end = Offset(size.width - 10f, scanY * density),
                                    strokeWidth = 3.dp.toPx()
                                )
                            }
                            Icon(Icons.Default.DocumentScanner, "Scan icon", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(text = scanStage, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { scanProgress },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Stepper Navigation
        Button(
            onClick = {
                val totalSteps = if (role == UserRole.PSYCHOLOGIST || role == UserRole.PSYCHOLOGY_STUDENT) 5 else 4
                if (step < totalSteps) {
                    if (step == 3 && !isSmsVerified) {
                        isSmsVerified = true 
                    }
                    step++
                } else {
                    // Finish Registration
                    viewModel.registerUser(
                        firstName = firstName.ifEmpty { "Krzysztof" },
                        lastName = lastName.ifEmpty { "Nowak" },
                        age = ageString.toIntOrNull() ?: 30,
                        gender = gender,
                        phone = phone.ifEmpty { "+48 501 222 333" },
                        email = email.ifEmpty { "user@wektor.pl" },
                        role = role,
                        isVerified = if (role == UserRole.PSYCHOLOGIST || role == UserRole.PSYCHOLOGY_STUDENT) isDocumentScanned else false,
                        qualifications = if (role == UserRole.PSYCHOLOGIST) "$qualType SWPS" else if (role == UserRole.PSYCHOLOGY_STUDENT) "Student Psychologii $qualType" else "",
                        specializations = if (role == UserRole.PSYCHOLOGIST || role == UserRole.PSYCHOLOGY_STUDENT) "Rodzina, Ogólny, Praca" else "",
                        pricePerSession = if (role == UserRole.PSYCHOLOGIST || role == UserRole.PSYCHOLOGY_STUDENT) 120.0 else 0.0,
                        bio = bio.ifEmpty { "Zarejestrowany użytkownik platformy Wektor." }
                    )
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                if (step == (if (role == UserRole.PSYCHOLOGIST || role == UserRole.PSYCHOLOGY_STUDENT) 5 else 4)) "Zakończ i Zapisz" else "Dalej",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}

@Composable
fun SocialAuthButtons(onSocialSelected: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Text(
                text = " LUB KONTYNUUJ PRZEZ ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Google Button
            Button(
                onClick = { onSocialSelected("Google") },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Google",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Google", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            // Facebook Button
            Button(
                onClick = { onSocialSelected("Facebook") },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.Facebook,
                    contentDescription = "Facebook",
                    tint = Color(0xFF1877F2),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Facebook", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun LoginScreen(
    viewModel: WektorViewModel,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cofnij")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Zaloguj się", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            "Witamy ponownie w aplikacji Wektor. Wpisz swój adres e-mail, aby uzyskać dostęp.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Adres E-mail") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Hasło") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Button(
            onClick = {
                if (email.isNotEmpty()) {
                    viewModel.loginUser(email)
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Zaloguj", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }

        Spacer(modifier = Modifier.height(24.dp))

        SocialAuthButtons(onSocialSelected = { provider ->
            viewModel.loginUser("${provider.lowercase()}@wektor.pl")
        })
    }
}
