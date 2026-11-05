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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.theme.OnScrim

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
        4 -> if (isPsychologist) "📓 Krok 4/5: Jak pisać notatki" else "📓 Krok 4/5: Notatki psychologa"
        5 -> "🔔 Krok 5/5: Centrum Powiadomień"
        else -> ""
    }

    val description = when (step) {
        1 -> "Kliknij na ikonę swojego profilu (awatar z inicjałami) w lewym górnym rogu ekranu. Otworzy się arkusz z Twoim profilem i funkcjami konta. Główne sekcje aplikacji (Ogłoszenia, Kalendarz, Czaty, Portfel) znajdziesz na dolnym pasku."
        2 -> "Kliknij ikonę „+” w górnym pasku, obok dzwonka powiadomień. Otworzy się ekran „Napisz ogłoszenie” — Twój wpis trafi automatycznie do właściwej sekcji, zależnie od Twojej roli."
        3 -> if (isPsychologist) {
            "Na górze strony zobaczysz dwie zakładki: „Szukam psychologa” (oferty innych psychologów) i „Szukam pacjenta” (zgłoszenia pacjentów szukających pomocy). Przełączaj się między nimi i kliknij dowolne ogłoszenie, aby zobaczyć profil autora i nawiązać kontakt."
        } else {
            "Na głównym ekranie zobaczysz listę ogłoszeń „Szukam psychologa” — oferty psychologów. Kliknij dowolne ogłoszenie, aby zobaczyć profil autora i nawiązać kontakt."
        }
        4 -> if (isPsychologist) {
            "Wchodząc do czatu z pacjentem, znajdziesz zakładkę „Notatki”. Możesz tam zapisać poufne notatki o sesjach, zaleceniach i przebiegu terapii. Pacjent ich nie widzi."
        } else {
            "Psycholog może prowadzić własne, poufne notatki o sesjach. Są dostępne tylko dla niego i nie pojawiają się w Twoim widoku czatu."
        }
        5 -> "Aplikacja nie wysyła powiadomień, więc nowe wiadomości, rezerwacje i odpowiedzi na ogłoszenia sprawdzaj bezpośrednio w czatach i kalendarzu. Dzwonek u góry otwiera Centrum Powiadomień, które na razie jest puste."
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.82f))
            .zIndex(100f)
            .clickable(enabled = true, onClick = {})
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step == 1) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(3.dp, OnScrim, CircleShape)
                            .clickable { onNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentUser?.firstName?.firstOrNull()?.toString()?.uppercase() ?: "K",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (step == 1) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("KLIKNIJ TU 👤", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (step == 2) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(3.dp, OnScrim, CircleShape)
                            .clickable { onNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Napisz ogłoszenie",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                if (step == 5) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(3.dp, OnScrim, CircleShape)
                            .clickable { onNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Powiadomienia",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                if (step == 4) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(3.dp, OnScrim, CircleShape)
                            .clickable { onNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Wiadomości",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            if (step == 3 && isPsychologist) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .border(3.dp, OnScrim, RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
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
                        Text("Szukam psychologa", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
        }

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
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

