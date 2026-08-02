package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entity.UserEntity
import com.example.ui.components.EmptyListPlaceholder
import com.example.ui.components.PsychologistCard
import com.example.ui.components.PatientCard
import com.example.ui.constants.ALL_SPECIALIZATIONS
import java.util.Locale

@Composable
fun FiltersSelectionCard(
    dashboardViewModel: DashboardViewModel,
    onDismiss: () -> Unit
) {
    val ageMin by dashboardViewModel.filterAgeMin.collectAsStateWithLifecycle()
    val ageMax by dashboardViewModel.filterAgeMax.collectAsStateWithLifecycle()
    val gender by dashboardViewModel.filterGender.collectAsStateWithLifecycle()
    val spec by dashboardViewModel.filterSpec.collectAsStateWithLifecycle()
    val verifiedOnly by dashboardViewModel.filterVerifiedOnly.collectAsStateWithLifecycle()
    val priceMax by dashboardViewModel.filterPriceMax.collectAsStateWithLifecycle()
    val minRating by dashboardViewModel.filterMinRating.collectAsStateWithLifecycle()

    var tempAgeMin by remember { mutableFloatStateOf(ageMin.toFloat()) }
    var tempAgeMax by remember { mutableFloatStateOf(ageMax.toFloat()) }
    var tempPriceMax by remember { mutableFloatStateOf(priceMax.toFloat()) }
    var tempMinRating by remember { mutableFloatStateOf(minRating.toFloat()) }
    var tempGender by remember { mutableStateOf(gender) }
    var tempSpec by remember { mutableStateOf(spec) }
    var tempVerifiedOnly by remember { mutableStateOf(verifiedOnly) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Filtrowanie wyników", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Wiek: ${tempAgeMin.toInt()} - ${tempAgeMax.toInt()} lat", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            RangeSlider(
                value = tempAgeMin..tempAgeMax,
                onValueChange = { range -> tempAgeMin = range.start; tempAgeMax = range.endInclusive },
                valueRange = 18f..80f,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cena do: ${tempPriceMax.toInt()} PLN", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Slider(
                    value = tempPriceMax,
                    onValueChange = { tempPriceMax = it },
                    valueRange = 50f..500f,
                    modifier = Modifier.weight(2f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Minimalna ocena: ${String.format(Locale.getDefault(), "%.1f", tempMinRating)}", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Slider(
                    value = tempMinRating,
                    onValueChange = { tempMinRating = it },
                    valueRange = 0f..5f,
                    modifier = Modifier.weight(2f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Specjalizacja", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            ScrollableTabRow(
                selectedTabIndex = (listOf("Wszystkie") + ALL_SPECIALIZATIONS).indexOf(tempSpec).coerceAtLeast(0),
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {},
                indicator = {}
            ) {
                (listOf("Wszystkie") + ALL_SPECIALIZATIONS).forEach { s ->
                    val selected = tempSpec == s
                    FilterChip(
                        selected = selected,
                        onClick = { tempSpec = s },
                        label = { Text(s, fontSize = 12.sp) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = tempVerifiedOnly, onCheckedChange = { tempVerifiedOnly = it })
                Text("Tylko zweryfikowani", fontSize = 13.sp)
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        dashboardViewModel.updateFilters(
                            tempAgeMin.toInt(),
                            tempAgeMax.toInt(),
                            tempGender,
                            tempSpec,
                            tempVerifiedOnly,
                            tempPriceMax.toDouble(),
                            tempMinRating.toDouble()
                        )
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Zastosuj")
                }
            }
        }
    }
}

@Composable
fun PsychologistCard(
    psych: UserEntity,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Profile Image / Placeholder
                val imageUri = psych.profileImageUri
                if (!imageUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(psych.firstName.firstOrNull()?.toString() ?: "?", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${psych.firstName} ${psych.lastName}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        if (psych.isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Verified, "Verified", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(psych.qualifications, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                        Text(" ${psych.rating} (${psych.ratingCount})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Ulubione",
                        tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Specs
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                psych.specializations.split(",").take(3).forEach { s ->
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(s.trim(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${psych.pricePerSession.toInt()} PLN / 50 min",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text("Zobacz Profil", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PatientCard(
    patient: UserEntity,
    canChat: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onStartChat: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(patient.firstName.firstOrNull()?.toString() ?: "?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${patient.firstName}, ${patient.age} lat", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Szuka pomocy: ${patient.specializations.ifEmpty { "Ogólna" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = patient.bio,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (canChat) {
                Button(
                    onClick = onStartChat,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Odpowiedz na zgłoszenie", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EmptyListPlaceholder(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 16.sp)
    }
}
