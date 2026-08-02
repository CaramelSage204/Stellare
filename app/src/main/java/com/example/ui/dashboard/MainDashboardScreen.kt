package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entity.UserEntity
import com.example.ui.navigation.Screen

@Composable
fun MainDashboardScreen(
    dashboardViewModel: DashboardViewModel,
    currentUser: UserEntity?,
    onOpenDrawer: () -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val searchQuery by dashboardViewModel.searchQuery.collectAsStateWithLifecycle()
    val roleFilter by dashboardViewModel.roleFilter.collectAsStateWithLifecycle() // "PSYCHOLOGIST" or "PATIENT"
    val isRefreshing by dashboardViewModel.isRefreshing.collectAsStateWithLifecycle()
    val favoritePsychologistsIds by dashboardViewModel.favoritePsychologistsIds.collectAsStateWithLifecycle()

    val filteredPsychologists by dashboardViewModel.filteredPsychologists.collectAsStateWithLifecycle()
    val filteredPatients by dashboardViewModel.filteredPatients.collectAsStateWithLifecycle()

    var showFiltersSheet by remember { mutableStateOf(false) }
    var showWriteOfferDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var activeTutorialStep by remember { mutableIntStateOf(0) }
    val showTutorialPrompt by dashboardViewModel.showTutorialPrompt.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenDrawer) {
                        val profileImageUri = currentUser?.profileImageUri
                        if (!profileImageUri.isNullOrEmpty()) {
                            AsyncImage(
                                model = profileImageUri,
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentUser?.firstName?.firstOrNull()?.toString()?.uppercase() ?: "?",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Wektor",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showNotificationsDialog = true }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Powiadomienia", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onNavigate(Screen.ChatList) }) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Wiadomości")
                    }
                }

                // Selection Tabs based on handdrawn sketch (кого вы ищете - psych, pacj)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { dashboardViewModel.setRoleFilter("PSYCHOLOGIST") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (roleFilter == "PSYCHOLOGIST") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Szukaj Psychologa",
                            color = if (roleFilter == "PSYCHOLOGIST") Color.Black else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { dashboardViewModel.setRoleFilter("PATIENT") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (roleFilter == "PATIENT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Szukaj Pacjenta",
                            color = if (roleFilter == "PATIENT") Color.Black else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Write offer button (below patient and psychologist)
                Button(
                    onClick = { showWriteOfferDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("write_offer_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Napisz ofertę",
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Napisz ofertę",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Search Bar and Filters trigger Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { dashboardViewModel.updateSearchQuery(it) },
                        placeholder = { Text("Wyszukaj po imieniu, frazie...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Szukaj") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { dashboardViewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = { showFiltersSheet = !showFiltersSheet },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = if (showFiltersSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filtry",
                            tint = if (showFiltersSheet) Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { dashboardViewModel.refreshData() },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Odśwież",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tutorial Invitation Dialog Prompt
            if (showTutorialPrompt) {
                AlertDialog(
                    onDismissRequest = { dashboardViewModel.setShowTutorialPrompt(false) },
                    title = { Text("Czy chcesz przejść szybki samouczek?") },
                    text = { Text("Dziękujemy za rejestrację w Wektor! Chcesz dowiedzieć się, jak otwierać swoje konto, pisać oferty, znajdować psychologów lub pacjentów, pisać notatki terapeutyczne oraz ustalić stawkę?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                dashboardViewModel.setShowTutorialPrompt(false)
                                activeTutorialStep = 1
                            }
                        ) {
                            Text("Tak, jasne!", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { dashboardViewModel.setShowTutorialPrompt(false) }
                        ) {
                            Text("Pomiń", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                )
            }



            // Real System Notifications Dialog
            if (showNotificationsDialog) {
                AlertDialog(
                    onDismissRequest = { showNotificationsDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Centrum Powiadomień", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Magdalena Nowak zaakceptowała Twój czat pomocowy.", fontSize = 13.sp)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Wektor: Twoje kwalifikacje zostały pomyślnie zweryfikowane!", fontSize = 13.sp)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Gray))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Witamy na platformie Wektor! Życzymy udanego dnia.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showNotificationsDialog = false }) {
                            Text("Zamknij")
                        }
                    }
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Filter dropdown expansion card if triggered
                AnimatedVisibility(visible = showFiltersSheet) {
                    FiltersSelectionCard(
                        dashboardViewModel = dashboardViewModel,
                        onDismiss = { showFiltersSheet = false }
                    )
                }

                if (isRefreshing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Odświeżanie...",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    if (roleFilter == "PSYCHOLOGIST") {
                        if (filteredPsychologists.isEmpty()) {
                            EmptyListPlaceholder(message = "Brak dostępnych psychologów spełniających kryteria.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(filteredPsychologists) { psych ->
                                    val isFav = favoritePsychologistsIds.contains(psych.id)
                                    PsychologistCard(
                                        psych = psych,
                                        isFavorite = isFav,
                                        onToggleFavorite = { dashboardViewModel.toggleFavorite(psych.id) },
                                        onClick = { onNavigate(Screen.PsychologistDetail(psych.id)) }
                                    )
                                }
                            }
                        }
                    } else {
                        if (filteredPatients.isEmpty()) {
                            EmptyListPlaceholder(message = "Brak zgłoszeń od pacjentów w tym momencie.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(filteredPatients) { patient ->
                                    val isPsychologist = currentUser?.role == "PSYCHOLOGIST" || currentUser?.role == "STUDENT"
                                    val isFav = favoritePsychologistsIds.contains(patient.id)
                                    PatientCard(
                                        patient = patient,
                                        canChat = isPsychologist,
                                        isFavorite = isFav,
                                        onToggleFavorite = { dashboardViewModel.toggleFavorite(patient.id) },
                                        onStartChat = { /* navigate to chat or call viewmodel start chat logic */ }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        // Write Offer Dialog
        if (showWriteOfferDialog) {
            Dialog(
                onDismissRequest = { showWriteOfferDialog = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = true
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WriteOfferDialog(
                        currentUser = currentUser,
                        onDismiss = { showWriteOfferDialog = false },
                        onPublish = { f, l, a, g, s, p, b, q ->
                            dashboardViewModel.publishOffer(f, l, a, g, s, p, b, q)
                        }
                    )
                }
            }
        }

        // Interactive Tutorial Overlay
        if (activeTutorialStep > 0) {
            WektorTutorialOverlay(
                step = activeTutorialStep,
                currentUser = currentUser,
                onNext = {
                    if (activeTutorialStep < 5) {
                        activeTutorialStep += 1
                    } else {
                        activeTutorialStep = 0
                    }
                },
                onSkip = {
                    activeTutorialStep = 0
                }
            )
        }
    }
}
