package com.example.stellare.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole

@Composable
fun StellareTutorialOverlay(
    step: Int,
    currentUser: UserModel?,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val isPsychologist = currentUser?.role == UserRole.PSYCHOLOGIST || currentUser?.role == UserRole.PSYCHOLOGY_STUDENT

    val title = when (step) {
        1 -> "👤 Krok 1/5: Jak otworzyć konto"
        2 -> "📝 Krok 2/5: Jak dodać ogłoszenie"
        3 -> "🔍 Krok 3/5: Gdzie szukać pomocy"
        4 -> "📓 Krok 4/5: Jak pisać notatki (Zametki)"
        5 -> "🔔 Krok 5/5: Centrum Powiadomień"
        else -> ""
    }

    val description = when (step) {
        1 -> "Kliknij na ikonę swojego profilu (awatar z inicjałami) w lewym górnym rogu ekranu. Spowoduje to wysunięcie panelu bocznego, z poziomu którego masz dostęp do wszystkich funkcji konta."
        2 -> "Kliknij ikonę „+” w górnym pasku, obok dzwonka powiadomień. Otworzy się ekran „Napisz ogłoszenie” — Twój wpis trafi automatycznie do właściwej sekcji, zależnie od Twojej roli."
        3 -> if (isPsychologist) {
            "Na górze strony zobaczysz dwie zakładki: „Szukam psychologa” (oferty innych psychologów) i „Szukam pacjenta” (zgłoszenia pacjentów szukających pomocy). Przełączaj się między nimi i kliknij dowolne ogłoszenie, aby zobaczyć profil autora i nawiązać kontakt."
        } else {
            "Na głównym ekranie zobaczysz listę ogłoszeń „Szukam psychologa” — oferty psychologów. Kliknij dowolne ogłoszenie, aby zobaczyć profil autora i nawiązać kontakt."
        }
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

                // 3. "Napisz ogłoszenie" add icon
                if (step == 2) {
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
                            imageVector = Icons.Default.Add,
                            contentDescription = "Napisz ogłoszenie",
                            tint = Color.Black
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // 4. Notifications (right-middle)
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

                // 5. Chat icon (far-right)
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

            // ROW 2: Tabs (Szukam psychologa, Szukam pacjenta) — tylko psycholog naprawdę je widzi
            if (step == 3 && isPsychologist) {
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
                        Text("Szukam psychologa", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { onNext() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Szukam pacjenta", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
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
                            Text("Pomyślna weryfikacja Twojego profilu przez admina Stellare.", fontSize = 11.sp, maxLines = 1)
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
