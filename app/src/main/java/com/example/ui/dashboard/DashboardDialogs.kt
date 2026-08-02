package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.UserEntity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WriteOfferDialog(
    currentUser: UserEntity?,
    onDismiss: () -> Unit,
    onPublish: (firstName: String, lastName: String, age: Int, gender: String, spec: String, price: Double, bio: String, qual: String) -> Unit
) {
    val firstName = currentUser?.firstName ?: ""
    val lastName = currentUser?.lastName ?: ""
    val age = currentUser?.age ?: 30
    val gender = currentUser?.gender ?: "Kobieta"
    val isPsychologist = currentUser?.role == "PSYCHOLOGIST" || currentUser?.role == "STUDENT"

    var bio by remember { mutableStateOf("") }
    var spec by remember { mutableStateOf(currentUser?.specializations ?: "") }
    var priceString by remember { mutableStateOf(currentUser?.pricePerSession?.toInt()?.toString() ?: "150") }
    var qual by remember { mutableStateOf(currentUser?.qualifications ?: "") }

    var showWriteOfferSpecDialog by remember { mutableStateOf(false) }
    var writeOfferSelectedSpecs by remember { mutableStateOf(currentUser?.specializations?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (isPsychologist) "Nowa Oferta Pomocy" else "Nowe Zgłoszenie Pacjenta") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Read-only user identity badge (App automatically reads the data)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = firstName.firstOrNull()?.toString()?.uppercase() ?: "?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "$firstName $lastName",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Profil: ${if (currentUser?.role == "PSYCHOLOGIST") "Psycholog" else if (currentUser?.role == "STUDENT") "Student Psychologii" else "Pacjent"} • $age lat • $gender",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Text(
                text = "Treść wpisu",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text(if (isPsychologist) "Opisz swoją ofertę / podejście terapeutyczne" else "Opisz z czym się zmagasz / jakiej pomocy szukasz") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(12.dp)
            )

            if (isPsychologist) {
                Text(
                    text = "Dodatkowe informacje dla pacjentów",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = qual,
                    onValueChange = { qual = it },
                    label = { Text("Kwalifikacje (np. Magister UJ, Certyfikat CBT)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showWriteOfferSpecDialog = true
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Specjalizacje Oferty",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edytuj",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (writeOfferSelectedSpecs.isEmpty()) {
                            Text(
                                text = "Brak wybranych specjalizacji. Kliknij aby wybrać.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                writeOfferSelectedSpecs.forEach { s ->
                                    SpecializationChip(
                                        text = s,
                                        isSelected = true
                                    )
                                }
                            }
                        }
                    }
                }

                if (showWriteOfferSpecDialog) {
                    var tempWriteOfferSpecs by remember { mutableStateOf(writeOfferSelectedSpecs) }
                    AlertDialog(
                        onDismissRequest = { showWriteOfferSpecDialog = false },
                        title = { 
                            Text(
                                "Wybierz Specjalizacje Oferty", 
                                fontWeight = FontWeight.Bold, 
                                color = MaterialTheme.colorScheme.onSurface
                            ) 
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "Zaznacz specjalizacje, które chcesz wyróżnić w tej ofercie. Kliknij na odpowiednie kółka, aby się podświetliły.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                val allSpecs = listOf(
                                    "Psycholog kliniczny",
                                    "Psychoterapeuta",
                                    "Seksuolog",
                                    "Psycholog par i małżeński",
                                    "Psycholog rodzinny",
                                    "Psycholog dziecięcy i młodzieżowy",
                                    "Psycholog szkolny",
                                    "Psycholog pracy i organizacji (biznesu)",
                                    "Psycholog sportu",
                                    "Psycholog transportu",
                                    "Interwent kryzysowy",
                                    "Psycholog sądowy"
                                )
                                
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    allSpecs.forEach { specName ->
                                        val isSelected = tempWriteOfferSpecs.contains(specName)
                                        SpecializationChip(
                                            text = specName,
                                            isSelected = isSelected,
                                            onClick = {
                                                tempWriteOfferSpecs = if (isSelected) {
                                                    tempWriteOfferSpecs - specName
                                                } else {
                                                    tempWriteOfferSpecs + specName
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    writeOfferSelectedSpecs = tempWriteOfferSpecs
                                    spec = tempWriteOfferSpecs.joinToString(", ")
                                    showWriteOfferSpecDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Zatwierdź", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWriteOfferSpecDialog = false }) {
                                Text("Anuluj", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                OutlinedTextField(
                    value = priceString,
                    onValueChange = { priceString = it },
                    label = { Text("Cena za godzinę (WKT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (bio.isNotEmpty()) {
                        onPublish(
                            firstName,
                            lastName,
                            age,
                            gender,
                            spec,
                            priceString.toDoubleOrNull() ?: 120.0,
                            bio,
                            qual
                        )
                        onDismiss()
                    }
                },
                enabled = bio.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Opublikuj wpis", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun WektorTutorialOverlay(
    step: Int,
    currentUser: UserEntity?,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val title = when (step) {
        1 -> "👤 Krok 1/5: Jak otworzyć konto"
        2 -> "📝 Krok 2/5: Jak dodać ofertę / post"
        3 -> "🔍 Krok 3/5: Gdzie szukać pomocy"
        4 -> "📓 Krok 4/5: Jak pisać notatki (Zametki)"
        5 -> "🔔 Krok 5/5: Centrum Powiadomień"
        else -> ""
    }

    val description = when (step) {
        1 -> "Kliknij na ikonę swojego profilu (awatar z inicjałami) w lewym górnym rogu ekranu. Spowoduje to wysunięcie panelu bocznego, z poziomu którego masz dostęp do wszystkich funkcji konta."
        2 -> "Kliknij przycisk „Napisz ofertę” pod zakładkami wyboru. Pozwoli Ci to opublikować nowy wpis. Zależnie od wybranej roli zostanie on automatycznie skierowany do sekcji psychologów lub klientów!"
        3 -> "Użyj zakładek „Szukaj Psychologa” lub „Szukaj Pacjenta” na górze strony, aby przeglądać zgłoszenia innych i łatwo nawiązać z nimi kontakt."
        4 -> "Wchodząc na dowolny czat pomocowy, znajdziesz specjalną zakładkę „ZAMETKI (Notatki)”. Tutaj psycholog może zapisać poufne notatki o sesjach, zaleceniach i przebiegu terapii pacjenta."
        5 -> "Kiedy otrzymasz nową wiadomość, rezerwację lub ktoś odpowie na ofertę, system natychmiast wyśle powiadomienie! Kliknięcie dzwonka u góry pokaże aktualne zdarzenia."
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .zIndex(100f)
            .clickable(enabled = true, onClick = {}) // Block and consume background clicks
    ) {
        // --- MIRRORED AND HIGHLIGHTED TOP BAR ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // ROW 1: Header (Avatar, pointers, notifications, messages)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Avatar (left)
                if (step == 1) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(3.dp, Color.White, CircleShape)
                            .clickable { onNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentUser?.firstName?.firstOrNull()?.toString()?.uppercase() ?: "K",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 2. Title (middle weight 1f) and pointer text
                if (step == 1) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("KLIKNIJ TU 👤", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // 3. Notifications (right-middle)
                if (step == 5) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(3.dp, Color.White, CircleShape)
                            .clickable { onNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Powiadomienia",
                            tint = Color.Black
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // 4. Chat icon (far-right)
                if (step == 4) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(3.dp, Color.White, CircleShape)
                            .clickable { onNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Wiadomości",
                            tint = Color.Black
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            // ROW 2: Tabs (Szukaj Psychologa, Szukaj Pacjenta)
            if (step == 3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .border(3.dp, Color.White, RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { onNext() }
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onNext() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Szukaj Psychologa 🔍", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { onNext() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Szukaj Pacjenta 👤", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }

            // ROW 3: Write Offer Button
            if (step == 2) {
                Button(
                    onClick = { onNext() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .border(3.dp, Color.White, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("KLIKNIJ TU: Napisz ofertę 📝", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }

            // Simulating opened notifications drawer in Step 5
            if (step == 5) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .align(Alignment.CenterHorizontally)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Otworzono Centrum Powiadomień", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pomyślna weryfikacja Twojego profilu przez admina Wektor.", fontSize = 11.sp, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Nowe zgłoszenie pacjenta w Twojej okolicy!", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        // --- EXPLANATION CARD (BOTTOM ALIGNED) ---
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(0.92f)
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = description,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onSkip) {
                        Text("Pomiń samouczek", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
                    }

                    Button(
                        onClick = onNext,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = if (step == 5) "Gotowe! 🎉" else "Dalej ➡️",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpecializationChip(
    text: String,
    isSelected: Boolean,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(24.dp)
    val modifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                } else {
                    Color(0xFF141110)
                },
                shape = shape
            )
            .border(
                width = 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color(0xFF322C2A)
                },
                shape = shape
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            },
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
