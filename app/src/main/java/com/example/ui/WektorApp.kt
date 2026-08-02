@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import coil.compose.AsyncImage
import com.example.data.local.entity.UserEntity
import com.example.ui.navigation.Screen
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.OnboardingScreen
import com.example.ui.auth.RegistrationScreen
import com.example.ui.calendar.CalendarScreen
import com.example.ui.chat.ChatRoomScreen
import com.example.ui.chat.MyClientsScreen
import com.example.ui.reviews.MyRatingsScreen
import androidx.compose.material3.HorizontalDivider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.dashboard.MainDashboardScreen
import com.example.ui.profile.FAQScreen
import com.example.ui.profile.ProfileEditScreen
import com.example.ui.psychologistdetail.PsychologistDetailScreen
import com.example.ui.wallet.WalletScreen

@Composable
fun WektorApp(viewModel: WektorViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val canNavigateBack by viewModel.canNavigateBack.collectAsStateWithLifecycle()

    BackHandler(enabled = canNavigateBack || isDrawerOpen) {
        if (isDrawerOpen) {
            viewModel.toggleDrawer(false)
        } else {
            viewModel.navigateBack()
        }
    }

    var totalDragX by remember { mutableStateOf(0f) }
    var isDragActive by remember { mutableStateOf(false) }
    var dragDirection by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val edgeWidthPx = remember(density) { with(density) { 40.dp.toPx() } }
    val swipeThresholdPx = remember(density) { with(density) { 100.dp.toPx() } }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInHorizontally() + fadeIn(),
            exit = slideOutHorizontally() + fadeOut(),
            modifier = Modifier.fillMaxHeight().fillMaxWidth(0.85f).zIndex(10f)
        ) {
            WektorNavigationDrawerContent(
                currentUser = currentUser,
                onClose = { viewModel.toggleDrawer(false) },
                onNavigate = { screen -> viewModel.navigateTo(screen) },
                onLogout = { viewModel.logout() },
                viewModel = viewModel
            )
        }

        if (isDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { viewModel.toggleDrawer(false) }
                    .zIndex(5f)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(canNavigateBack) {
                    if (canNavigateBack) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val width = size.width
                                if (offset.x < edgeWidthPx) {
                                    isDragActive = true
                                    dragDirection = 1
                                    totalDragX = 0f
                                } else if (offset.x > width - edgeWidthPx) {
                                    isDragActive = true
                                    dragDirection = -1
                                    totalDragX = 0f
                                } else {
                                    isDragActive = false
                                    dragDirection = 0
                                }
                            },
                            onDragEnd = {
                                if (isDragActive) {
                                    if (dragDirection == 1 && totalDragX > swipeThresholdPx) {
                                        viewModel.navigateBack()
                                    } else if (dragDirection == -1 && totalDragX < -swipeThresholdPx) {
                                        viewModel.navigateBack()
                                    }
                                }
                                isDragActive = false
                                dragDirection = 0
                                totalDragX = 0f
                            },
                            onDragCancel = {
                                isDragActive = false
                                dragDirection = 0
                                totalDragX = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (isDragActive) {
                                    totalDragX += dragAmount
                                    change.consume()
                                }
                            }
                        )
                    }
                },
            color = MaterialTheme.colorScheme.background
        ) {
            Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                when (screen) {
                    is Screen.Onboarding -> OnboardingScreen(
                        onNavigateToRegister = { viewModel.navigateTo(Screen.Registration) },
                        onNavigateToLogin = { viewModel.navigateTo(Screen.Login) },
                        onSkipToDashboard = {
                            viewModel.registerUser(
                                firstName = "Jan",
                                lastName = "Kowalski",
                                age = 30,
                                gender = "Mężczyzna",
                                phone = "+48 500 000 000",
                                email = "jan@wektor.pl",
                                role = "PATIENT",
                                bio = "Zalogowany jako pacjent testowy do celów eksploracji."
                            )
                        }
                    )
                    is Screen.Registration -> RegistrationScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                    is Screen.Login -> LoginScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                    is Screen.MainDashboard -> {
                        val dashboardViewModel: DashboardViewModel = viewModel()
                        MainDashboardScreen(
                            dashboardViewModel = dashboardViewModel,
                            currentUser = currentUser,
                            onOpenDrawer = { viewModel.toggleDrawer(true) },
                            onNavigate = { viewModel.navigateTo(it) }
                        )
                    }
                    is Screen.PsychologistDetail -> PsychologistDetailScreen(
                        viewModel = viewModel,
                        psychologistId = screen.psychologistId,
                        onBack = { viewModel.navigateBack() }
                    )
                    is Screen.ChatRoom -> ChatRoomScreen(
                        viewModel = viewModel,
                        chatId = screen.chatId,
                        onBack = { viewModel.navigateBack() }
                    )
                    is Screen.ChatList -> MyClientsScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                    is Screen.MyRatings -> MyRatingsScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                    is Screen.MyClients -> MyClientsScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                    is Screen.ProfileEdit -> ProfileEditScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                    is Screen.FAQ -> FAQScreen(
                        onBack = { viewModel.navigateBack() }
                    )
                    is Screen.Calendar -> CalendarScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                    is Screen.Wallet -> WalletScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }
        }
    }
}

@Composable
fun WektorNavigationDrawerContent(
    currentUser: UserEntity?,
    onClose: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onLogout: () -> Unit,
    viewModel: WektorViewModel
) {
    val favoritePsychologists by viewModel.favoritePsychologists.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            .padding(20.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!currentUser?.profileImageUri.isNullOrEmpty()) {
                AsyncImage(
                    model = currentUser.profileImageUri,
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentUser?.firstName?.firstOrNull()?.toString()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentUser?.let { "${it.firstName} ${it.lastName}" } ?: "Gość",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (currentUser?.isVerified == true) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = when (currentUser?.role) {
                        "PSYCHOLOGIST" -> "Psycholog"
                        "STUDENT" -> "Student Psychologii"
                        else -> "Pacjent"
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = currentUser?.email ?: "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (currentUser != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xFFFFD700).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Paid,
                            contentDescription = "Monety",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${currentUser.coinsBalance} WKT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        Spacer(modifier = Modifier.height(16.dp))

        DrawerItem(
            icon = Icons.Default.Home,
            label = "Pulpit Główny",
            onClick = {
                onNavigate(Screen.MainDashboard)
                onClose()
            }
        )

        if (currentUser?.role == "PSYCHOLOGIST") {
            DrawerItem(
                icon = Icons.Default.Star,
                label = "Moje Oceny i Opinie",
                onClick = {
                    onNavigate(Screen.MyRatings)
                    onClose()
                }
            )
        }

        val isPsych = currentUser?.role == "PSYCHOLOGIST" || currentUser?.role == "STUDENT"

        DrawerItem(
            icon = Icons.Default.Chat,
            label = if (isPsych) "Moje Czaty i Klienci" else "Moje Czaty i Psycholodzy",
            onClick = {
                onNavigate(Screen.ChatList)
                onClose()
            }
        )

        DrawerItem(
            icon = Icons.Default.Person,
            label = "Mój Profil",
            onClick = {
                onNavigate(Screen.ProfileEdit)
                onClose()
            }
        )

        DrawerItem(
            icon = Icons.Default.DateRange,
            label = "Mój Kalendarz i Wizyty",
            onClick = {
                onNavigate(Screen.Calendar)
                onClose()
            }
        )

        DrawerItem(
            icon = Icons.Default.Info,
            label = "Zalety i FAQ Wektor",
            onClick = {
                onNavigate(Screen.FAQ)
                onClose()
            }
        )

        if (favoritePsychologists.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isPsych) "Polubieni Pacjenci" else "Polubieni Psycholodzy",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                favoritePsychologists.forEach { psych ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isPsych) {
                                    viewModel.startChatWith(psych.id)
                                } else {
                                    onNavigate(Screen.PsychologistDetail(psych.id))
                                }
                                onClose()
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!psych.profileImageUri.isNullOrEmpty()) {
                                AsyncImage(
                                    model = psych.profileImageUri,
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
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = psych.firstName.firstOrNull()?.toString()?.uppercase() ?: "?",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${psych.firstName} ${psych.lastName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isPsych) "Pacjent • ${psych.age} lat" else (psych.specializations.split(",").firstOrNull() ?: "Psycholog"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleFavorite(psych.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Usuń z ulubionych",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        DrawerItem(
            icon = Icons.Default.ExitToApp,
            label = "Wyloguj Się",
            textColor = MaterialTheme.colorScheme.error,
            iconColor = MaterialTheme.colorScheme.error,
            onClick = {
                onLogout()
                onClose()
            }
        )
    }
}

@Composable
fun DrawerItem(
    icon: ImageVector,
    label: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, fontSize = 16.sp, color = textColor, fontWeight = FontWeight.Medium)
    }
}
