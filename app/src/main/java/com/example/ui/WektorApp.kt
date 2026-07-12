@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ChatEntity
import com.example.data.MessageEntity
import com.example.data.NoteEntity
import com.example.data.UserEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var dragDirection by remember { mutableStateOf(0) } // 1 for left-to-right, -1 for right-to-left
    val density = LocalDensity.current
    val edgeWidthPx = remember(density) { with(density) { 40.dp.toPx() } }
    val swipeThresholdPx = remember(density) { with(density) { 100.dp.toPx() } }

    Box(modifier = Modifier.fillMaxSize()) {
        // Simple and robust slide-out Custom Drawer implementation to ensure full control & zero crashes
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

        // Drawer Overlay Dimmer
        if (isDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { viewModel.toggleDrawer(false) }
                    .zIndex(5f)
            )
        }

        // Main screen navigation container
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
                            // Automatically registers a test patient so the user can easily explore
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
                    is Screen.MainDashboard -> MainDashboardScreen(
                        viewModel = viewModel,
                        currentUser = currentUser,
                        onOpenDrawer = { viewModel.toggleDrawer(true) },
                        onNavigate = { viewModel.navigateTo(it) }
                    )
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
// ------------------ NAVIGATION DRAWER CONTENT ------------------

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
        // Drawer Header with profile card
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!currentUser?.profileImageUri.isNullOrEmpty()) {
                AsyncImage(
                    model = currentUser!!.profileImageUri,
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

        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        Spacer(modifier = Modifier.height(16.dp))

        // Navigation list
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
                onNavigate(Screen.MyClients)
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
            icon = Icons.Default.AccountBalanceWallet,
            label = "Mój Portfel monet",
            onClick = {
                onNavigate(Screen.Wallet)
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
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
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

// ------------------ ONBOARDING SCREEN ------------------

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

// ------------------ REGISTRATION SCREEN (5-STEP FLOW) ------------------

@Composable
fun RegistrationScreen(
    viewModel: WektorViewModel,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(1) }

    // Forms states
    var role by remember { mutableStateOf("PATIENT") } // "PATIENT" or "PSYCHOLOGIST"
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
                text = "Krok $step z ${if (role == "PSYCHOLOGIST" || role == "STUDENT") 5 else 4}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Stepper Progress Bar
        LinearProgressIndicator(
            progress = { step.toFloat() / if (role == "PSYCHOLOGIST" || role == "STUDENT") 5f else 4f },
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
                                width = if (role == "PATIENT") 2.dp else 1.dp,
                                color = if (role == "PATIENT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .background(
                                color = if (role == "PATIENT") MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { role = "PATIENT" }
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
                        RadioButton(selected = role == "PATIENT", onClick = { role = "PATIENT" })
                    }

                    // Psychologist Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (role == "PSYCHOLOGIST") 2.dp else 1.dp,
                                color = if (role == "PSYCHOLOGIST") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .background(
                                color = if (role == "PSYCHOLOGIST") MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { role = "PSYCHOLOGIST" }
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
                        RadioButton(selected = role == "PSYCHOLOGIST", onClick = { role = "PSYCHOLOGIST" })
                    }

                    // Student Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (role == "STUDENT") 2.dp else 1.dp,
                                color = if (role == "STUDENT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .background(
                                color = if (role == "STUDENT") MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { role = "STUDENT" }
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
                        RadioButton(selected = role == "STUDENT", onClick = { role = "STUDENT" })
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
                    label = { Text(if (role == "PSYCHOLOGIST") "Twój krótki biogram / doświadczenie" else "Czego poszukujesz / Twoje obawy") },
                    modifier = Modifier.fillMaxWidth().height(120.dp).padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            3 -> {
                // STEP 3: Phone & SMS verification simulator (Screenshot 2: -wpisać numer telefonu -otrzymać kod sms...)
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
                // STEP 5: Qualification verification for Psychologist (Screenshot 3: -zeskanować dokument tożsamości a potem dyplom)
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
                val totalSteps = if (role == "PSYCHOLOGIST" || role == "STUDENT") 5 else 4
                if (step < totalSteps) {
                    // Check step validation
                    if (step == 2 && (firstName.isEmpty() || lastName.isEmpty() || ageString.isEmpty())) {
                        // validation warning (could display toast, let's just proceed or require)
                    }
                    if (step == 3 && !isSmsVerified) {
                        isSmsVerified = true // auto bypass for easy testing if they click next without verification
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
                        isVerified = if (role == "PSYCHOLOGIST" || role == "STUDENT") isDocumentScanned else false,
                        qualifications = if (role == "PSYCHOLOGIST") "$qualType SWPS" else if (role == "STUDENT") "Student Psychologii $qualType" else "",
                        specializations = if (role == "PSYCHOLOGIST" || role == "STUDENT") "Rodzina, Ogólny, Praca" else "",
                        pricePerSession = if (role == "PSYCHOLOGIST" || role == "STUDENT") 120.0 else 0.0,
                        bio = bio.ifEmpty { "Zarejestrowany użytkownik platformy Wektor." }
                    )
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                if (step == (if (role == "PSYCHOLOGIST" || role == "STUDENT") 5 else 4)) "Zakończ i Zapisz" else "Dalej",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}

// ------------------ SOCIAL AUTH & LOGIN SCREENS ------------------

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

// ------------------ MAIN DASHBOARD SCREEN ------------------

@Composable
fun MainDashboardScreen(
    viewModel: WektorViewModel,
    currentUser: UserEntity?,
    onOpenDrawer: () -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val roleFilter by viewModel.roleFilter.collectAsStateWithLifecycle() // "PSYCHOLOGIST" or "PATIENT"
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val favoritePsychologistsIds by viewModel.favoritePsychologistsIds.collectAsStateWithLifecycle()

    val filteredPsychologists by viewModel.filteredPsychologists.collectAsStateWithLifecycle()
    val filteredPatients by viewModel.filteredPatients.collectAsStateWithLifecycle()

    var showFiltersSheet by remember { mutableStateOf(false) }
    var showWriteOfferDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var activeTutorialStep by remember { mutableStateOf<Int?>(null) }
    val showTutorialPrompt by viewModel.showTutorialPrompt.collectAsStateWithLifecycle()

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
                        if (!currentUser?.profileImageUri.isNullOrEmpty()) {
                            AsyncImage(
                                model = currentUser!!.profileImageUri,
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
                    IconButton(onClick = { onNavigate(Screen.MyClients) }) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Wiadomości")
                    }
                }

                // Selection Tabs based on handdrawn sketch (кого вы ищете - psych, pacj)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.setRoleFilter("PSYCHOLOGIST") },
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
                        onClick = { viewModel.setRoleFilter("PATIENT") },
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
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("Wyszukaj po imieniu, frazie...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Szukaj") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
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
                        onClick = { viewModel.refreshData() },
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
                    onDismissRequest = { viewModel.setShowTutorialPrompt(false) },
                    title = { Text("Czy chcesz przejść szybki samouczek?") },
                    text = { Text("Dziękujemy za rejestrację w Wektor! Chcesz dowiedzieć się, jak otwierać swoje konto, pisać oferty, znajdować psychologów lub pacjentów, pisać notatki terapeutyczne oraz ustalić stawkę?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.setShowTutorialPrompt(false)
                                activeTutorialStep = 1
                            }
                        ) {
                            Text("Tak, jasne!", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { viewModel.setShowTutorialPrompt(false) }
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
                        viewModel = viewModel,
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
                                        onToggleFavorite = { viewModel.toggleFavorite(psych.id) },
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
                                        onToggleFavorite = { viewModel.toggleFavorite(patient.id) },
                                        onStartChat = { viewModel.startChatWith(patient.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Write Offer Dialog (rendered as a fullscreen Dialog to be completely on top of everything and hide the top bar/scaffold)
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
                            viewModel.publishOffer(f, l, a, g, s, p, b, q)
                        }
                    )
                }
            }
        }

        // Interactive Tutorial Overlay at top level (sibling of Scaffold) to block all touch interactions
        activeTutorialStep?.let { step ->
            WektorTutorialOverlay(
                step = step,
                currentUser = currentUser,
                onNext = {
                    if (step < 5) {
                        activeTutorialStep = step + 1
                    } else {
                        activeTutorialStep = null
                    }
                },
                onSkip = {
                    activeTutorialStep = null
                }
            )
        }
    }
}
}

// ------------------ DETAILED FILTER EXPANSION CARD ------------------

@Composable
fun FiltersSelectionCard(
    viewModel: WektorViewModel,
    onDismiss: () -> Unit
) {
    val filterAgeMin by viewModel.filterAgeMin.collectAsStateWithLifecycle()
    val filterAgeMax by viewModel.filterAgeMax.collectAsStateWithLifecycle()
    val filterGender by viewModel.filterGender.collectAsStateWithLifecycle()
    val filterSpec by viewModel.filterSpec.collectAsStateWithLifecycle()
    val filterVerifiedOnly by viewModel.filterVerifiedOnly.collectAsStateWithLifecycle()
    val filterPriceMax by viewModel.filterPriceMax.collectAsStateWithLifecycle()
    val filterMinRating by viewModel.filterMinRating.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Filtrowanie wyników", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            // Specialization
            Text("Kierunek / Specjalizacja", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Wszystkie", "Rodzina", "Depresja", "Życie intymne", "Praca", "Lęki", "Ogólny").forEach { spec ->
                    FilterChip(
                        selected = filterSpec == spec,
                        onClick = { viewModel.updateFilters(filterAgeMin, filterAgeMax, filterGender, spec, filterVerifiedOnly, filterPriceMax, filterMinRating) },
                        label = { Text(spec) }
                    )
                }
            }

            // Gender selector
            Text("Płeć psychologa", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Wszystkie", "Kobieta", "Mężczyzna").forEach { gen ->
                    FilterChip(
                        selected = filterGender == gen,
                        onClick = { viewModel.updateFilters(filterAgeMin, filterAgeMax, gen, filterSpec, filterVerifiedOnly, filterPriceMax, filterMinRating) },
                        label = { Text(gen) }
                    )
                }
            }

            // Verified state checkbox
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = filterVerifiedOnly,
                    onCheckedChange = { viewModel.updateFilters(filterAgeMin, filterAgeMax, filterGender, filterSpec, it, filterPriceMax, filterMinRating) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tylko zweryfikowani dyplomem psycholodzy", fontSize = 14.sp)
            }

            // Price max slider
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Maksymalna cena sesji", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text("${filterPriceMax.toInt()} WKT/h", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = filterPriceMax.toFloat(),
                onValueChange = { viewModel.updateFilters(filterAgeMin, filterAgeMax, filterGender, filterSpec, filterVerifiedOnly, it.toDouble(), filterMinRating) },
                valueRange = 50f..400f,
                modifier = Modifier.fillMaxWidth()
            )

            // Age min/max simple selectors
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Zakres wieku", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text("${filterAgeMin} - ${filterAgeMax} lat")
            }
            RangeSlider(
                value = filterAgeMin.toFloat()..filterAgeMax.toFloat(),
                onValueChange = { range ->
                    viewModel.updateFilters(range.start.toInt(), range.endInclusive.toInt(), filterGender, filterSpec, filterVerifiedOnly, filterPriceMax, filterMinRating)
                },
                valueRange = 18f..80f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    viewModel.updateFilters(18, 100, "Wszystkie", "Wszystkie", false, 400.0, 0.0)
                }) {
                    Text("Resetuj")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = onDismiss) {
                    Text("Zastosuj")
                }
            }
        }
    }
}

// ------------------ PSYCHOLOGIST CARD ------------------

@Composable
fun PsychologistCard(
    psych: UserEntity,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("psychologist_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = psych.firstName.firstOrNull()?.toString() ?: "?",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${psych.firstName} ${psych.lastName}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (psych.isVerified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified badge",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = psych.qualifications,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Heart Favorite
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Ulubione",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Specific details layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rating row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = "Ocena", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${"%.1f".format(psych.rating)} (${psych.ratingCount})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Price Row
                Text(
                    text = "${psych.pricePerSession.toInt()} WKT / h",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Specialization chips
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                psych.specializations.split(",").map { it.trim() }.forEach { tag ->
                    if (tag.isNotEmpty()) {
                        SpecializationChip(
                            text = tag,
                            isSelected = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "Zajawka",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ZAJAWKA OFERTY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = psych.bio,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 3
                    )
                }
            }
        }
    }
}

// ------------------ PATIENT CARD ------------------

@Composable
fun PatientCard(
    patient: UserEntity,
    canChat: Boolean = true,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onStartChat: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = patient.firstName.firstOrNull()?.toString() ?: "?",
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${patient.firstName}, ${patient.age} lat",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Klient / Pacjent",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (canChat) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Ulubione",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "Zajawka",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ZAJAWKA ZGŁOSZENIA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = patient.bio,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (canChat) {
                Button(
                    onClick = onStartChat,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "Czat")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rozpocznij czat pomocowy", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tylko psycholodzy mogą odpowiadać na zgłoszenia pacjentów.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// ------------------ EMPTY LIST PLACEHOLDER ------------------

@Composable
fun EmptyListPlaceholder(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Brak wyników",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

// ------------------ PSYCHOLOGIST DETAIL SCREEN ------------------

@Composable
fun PsychologistDetailScreen(
    viewModel: WektorViewModel,
    psychologistId: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var psych by remember { mutableStateOf<UserEntity?>(null) }
    val reviews by viewModel.getReviewsForPsychologist(psychologistId).collectAsStateWithLifecycle(emptyList())

    var showCertificateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(psychologistId) {
        psych = viewModel.getUserById(psychologistId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil Psychologa") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrót")
                    }
                },
                actions = {
                    val favoritePsychologistsIds by viewModel.favoritePsychologistsIds.collectAsStateWithLifecycle()
                    val isFav = favoritePsychologistsIds.contains(psychologistId)
                    IconButton(onClick = { viewModel.toggleFavorite(psychologistId) }) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Ulubione",
                            tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        psych?.let { user ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.firstName.firstOrNull()?.toString() ?: "?",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${user.firstName} ${user.lastName}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (user.isVerified) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified badge",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = user.qualifications,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick info row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("OCENA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, "Star", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${"%.1f".format(user.rating)} (${user.ratingCount})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CENA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("${user.pricePerSession.toInt()} WKT/h", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("WIEK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("${user.age} lat", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }

                // Bio
                Text("O mnie", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    text = user.bio,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Specializations
                Text("Kierunki pomocy", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                
                val userSpecsSet = user.specializations.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                    
                    allSpecs.forEach { specName ->
                        val isSelected = userSpecsSet.contains(specName)
                        SpecializationChip(
                            text = specName,
                            isSelected = isSelected
                        )
                    }
                }

                // Uploaded scanned documents block (Screenshot 3 & 5: profil zweryfikowany: dośw, certyfikaty)
                if (user.isVerified) {
                    Text("Zweryfikowane Dokumenty", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                            .clickable { showCertificateDialog = true },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, "Cert", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dyplom_Uczelni_Skan.pdf", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Certyfikat_Zweryfikowany_Przez_Wektor", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.Default.Visibility, "View", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Real time slots booking
                val availableSlots by viewModel.getFreeSlotsForPsychologist(psychologistId).collectAsStateWithLifecycle(emptyList())
                var selectedAppointment by remember { mutableStateOf<com.example.data.AppointmentEntity?>(null) }
                var showBookingDialog by remember { mutableStateOf(false) }
                var bookingNotes by remember { mutableStateOf("") }
                var bookingSuccess by remember { mutableStateOf(false) }

                Text("Rezerwacja sesji online", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
                
                if (availableSlots.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventBusy, "No slots", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Brak dostępnych wolnych terminów na ten moment.",
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Napisz w czacie do psychologa, aby uzgodnić wolny termin.",
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    Text("Wybierz dogodny termin z kalendarza psychologa:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        availableSlots.forEach { appointment ->
                            val isSelected = selectedAppointment?.appointmentId == appointment.appointmentId
                            val dateParts = appointment.date.split("-")
                            val shortDate = if (dateParts.size == 3) "${dateParts[2]}.${dateParts[1]}" else appointment.date
                            val slotLabel = "$shortDate o ${appointment.time}"
                            
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedAppointment = appointment }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = slotLabel,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Booking Button & Quick Chat Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.startChatWith(user.id) },
                        modifier = Modifier.weight(1.5f).height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = "Czat")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rozpocznij czat", fontWeight = FontWeight.Bold, color = Color.Black)
                    }

                    if (selectedAppointment != null) {
                        Button(
                            onClick = { showBookingDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1.5f).height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Book", tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rezerwuj termin", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }

                if (showBookingDialog && selectedAppointment != null) {
                    AlertDialog(
                        onDismissRequest = { showBookingDialog = false },
                        title = { Text("Potwierdź rezerwację") },
                        text = {
                            Column {
                                val dateParts = selectedAppointment!!.date.split("-")
                                val displayDate = if (dateParts.size == 3) "${dateParts[2]}.${dateParts[1]}.${dateParts[0]}" else selectedAppointment!!.date
                                Text("Rezerwujesz sesję u psychologa: ${user.firstName} ${user.lastName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Termin: $displayDate o godzinie ${selectedAppointment!!.time}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD700).copy(alpha = 0.1f)),
                                    border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Paid,
                                            contentDescription = "Portfel",
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            val costInCoins = user.pricePerSession.toInt()
                                            Text(
                                                text = "Koszt rezerwacji: $costInCoins monet WKT",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val balance = currentUser?.coinsBalance ?: 0
                                            Text(
                                                text = "Twój balans: $balance monet WKT",
                                                fontSize = 11.sp,
                                                color = if (balance < costInCoins) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = bookingNotes,
                                    onValueChange = { bookingNotes = it },
                                    label = { Text("Notatka dla psychologa (opcjonalnie)") },
                                    placeholder = { Text("Napisz krótko, czego dotyczy problem...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )
                            }
                        },
                        confirmButton = {
                            val balance = currentUser?.coinsBalance ?: 0
                            val costInCoins = user.pricePerSession.toInt()
                            if (balance >= costInCoins) {
                                Button(
                                    onClick = {
                                        val success = viewModel.bookAppointment(selectedAppointment!!.appointmentId, bookingNotes, costInCoins = costInCoins)
                                        if (success) {
                                            showBookingDialog = false
                                            bookingSuccess = true
                                        }
                                    }
                                ) {
                                    Text("Zatwierdź ($costInCoins monet)", color = Color.Black)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        showBookingDialog = false
                                        viewModel.navigateTo(Screen.Wallet)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                                ) {
                                    Text("Doładuj portfel monet", color = Color.Black)
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBookingDialog = false }) {
                                Text("Anuluj")
                            }
                        }
                    )
                }

                if (bookingSuccess) {
                    AlertDialog(
                        onDismissRequest = { 
                            bookingSuccess = false
                            selectedAppointment = null
                        },
                        title = { Text("Pomyślna rezerwacja! 🎉") },
                        text = {
                            Text("Twoja wizyta została pomyślnie zarezerwowana. Znajdziesz ją w sekcji \"Mój Kalendarz i Wizyty\" w menu bocznym.")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    bookingSuccess = false
                                    selectedAppointment = null
                                    viewModel.navigateTo(Screen.Calendar)
                                }
                            ) {
                                Text("Przejdź do Kalendarza", color = Color.Black)
                            }
                        }
                    )
                }

                // Stars Breakdown (as in Screenshot 8 handdrawn diagram: "4,5" star rating layout)
                Text("Opinie pacjentów i oceny", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(end = 24.dp)
                    ) {
                        Text(
                            text = "%.1f".format(user.rating),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row {
                            repeat(5) {
                                Icon(Icons.Default.Star, "Star", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${reviews.size} opinii", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }

                    // Progress bars breakdown (from Screenshot 8 handdrawn details)
                    Column(modifier = Modifier.weight(1f)) {
                        val reviewLevels = listOf(
                            Pair("5", 0.7f),
                            Pair("4", 0.2f),
                            Pair("3", 0.05f),
                            Pair("2", 0.03f),
                            Pair("1", 0.02f)
                        )
                        reviewLevels.forEach { (level, pct) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(level, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(12.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${(pct * 10).toInt()}", fontSize = 11.sp, modifier = Modifier.width(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Real Patient Reviews listing
                reviews.forEach { rev ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(rev.reviewerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Row {
                                    repeat(rev.rating) {
                                        Icon(Icons.Default.Star, "Star", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(rev.comment, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Dodano: " + DateUtils.getRelativeTimeSpanString(rev.timestamp).toString(),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        // Animated zoom of verified certificates/diplomas
        if (showCertificateDialog) {
            Dialog(onDismissRequest = { showCertificateDialog = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Zweryfikowany Dyplom Wektor", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { showCertificateDialog = false }) {
                                Icon(Icons.Default.Close, "Dismiss")
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // Visual display of simulated professional diploma
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Color(0xFFFEFDF9), RoundedCornerShape(12.dp))
                                .border(3.dp, Color(0xFFC5A880), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Verified, "Cert stamp", tint = Color(0xFFC5A880), modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("DYPLOM UKOŃCZENIA STUDIÓW", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                Text("Wydział Psychologii i Nauk Społecznych", fontSize = 11.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Specjalizacja: Psychologia Kliniczna", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("ZWERYFIKOWANE SYSTEMOWO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------ CHAT SCREEN with ZAMETKI (NOTES) TAB ------------------

@Composable
fun ChatRoomScreen(
    viewModel: WektorViewModel,
    chatId: Int,
    onBack: () -> Unit
) {
    val messages by viewModel.getMessagesForChat(chatId).collectAsStateWithLifecycle(emptyList())
    val notes by viewModel.getNotesForChat(chatId).collectAsStateWithLifecycle(emptyList())
    val participants by viewModel.chatParticipants.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var chatTab by remember { mutableStateOf("CHAT") } // "CHAT" or "NOTES"
    var showCallOverlay by remember { mutableStateOf<String?>(null) } // null, "VOICE", "VIDEO"
    var callDurationSeconds by remember { mutableStateOf(0) }

    // Resolving other user info
    var chatSession by remember { mutableStateOf<ChatEntity?>(null) }
    var otherUser by remember { mutableStateOf<UserEntity?>(null) }

    LaunchedEffect(chatId, participants, currentUser) {
        val chats = viewModel.activeChats.value
        val chat = chats.find { it.chatId == chatId }
        chatSession = chat
        val currUser = currentUser
        if (chat != null && currUser != null) {
            val otherId = if (chat.psychologistId == currUser.id) chat.patientId else chat.psychologistId
            otherUser = participants[otherId]
        }
    }

    var textInput by remember { mutableStateOf("") }

    // Notes creation states (Zametki)
    var noteTitleInput by remember { mutableStateOf("") }
    var noteContentInput by remember { mutableStateOf("") }
    var isAddingNote by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                // Header details
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cofnij")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(otherUser?.firstName?.firstOrNull()?.toString() ?: "?", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = otherUser?.let { "${it.firstName} ${it.lastName}" } ?: "Ładowanie...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Online", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    IconButton(onClick = { showCallOverlay = "VOICE" }) {
                        Icon(Icons.Default.Call, contentDescription = "Zadzwoń", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showCallOverlay = "VIDEO" }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Wideo", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Tabs - Chat vs Zametki (Notes are a core psychologist asset from Screenshot 8 handdrawn diagram "moje chaty / zametki")
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { chatTab = "CHAT" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (chatTab == "CHAT") MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ChatBubble, contentDescription = "Chat", tint = if (chatTab == "CHAT") Color.Black else MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rozmowa", color = if (chatTab == "CHAT") Color.Black else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { chatTab = "NOTES" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (chatTab == "NOTES") MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NoteAlt, contentDescription = "Notes", tint = if (chatTab == "NOTES") Color.Black else MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ZAMETKI (Notatki)", color = if (chatTab == "NOTES") Color.Black else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
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
            if (chatTab == "CHAT") {
                // CHAT LAYOUT
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        reverseLayout = false,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages) { msg ->
                            val isOutgoing = currentUser?.let { msg.senderId == it.id } ?: false
                            MessageBubble(message = msg, isOutgoing = isOutgoing)
                        }
                    }

                    // Input bottom row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("Napisz wiadomość...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                            singleLine = false
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (textInput.isNotEmpty()) {
                                    viewModel.sendChatMessage(chatId, textInput)
                                    textInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.Black)
                        }
                    }
                }
            } else {
                // ZAMETKI (NOTES) LAYOUT (Screenshot 8: note history and detail view for psychologist private logs)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Moje Notatki z Terapii", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { isAddingNote = !isAddingNote }) {
                            Icon(
                                imageVector = if (isAddingNote) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = "Add note",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    AnimatedVisibility(visible = isAddingNote) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Nowa Zametka (Notatka)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                OutlinedTextField(
                                    value = noteTitleInput,
                                    onValueChange = { noteTitleInput = it },
                                    label = { Text("Tytuł (np. Sesja 2)") },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                )
                                OutlinedTextField(
                                    value = noteContentInput,
                                    onValueChange = { noteContentInput = it },
                                    label = { Text("Treść / wnioski / zadania domowe...") },
                                    modifier = Modifier.fillMaxWidth().height(100.dp).padding(bottom = 16.dp)
                                )
                                Button(
                                    onClick = {
                                        if (noteTitleInput.isNotEmpty() && noteContentInput.isNotEmpty()) {
                                            viewModel.saveClientNote(chatId, noteTitleInput, noteContentInput)
                                            noteTitleInput = ""
                                            noteContentInput = ""
                                            isAddingNote = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Zapisz Notatkę", fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (notes.isEmpty()) {
                        EmptyListPlaceholder(message = "Brak notatek do tego pacjenta. Dodaj pierwszą, aby śledzić postępy.")
                    } else {
                        notes.forEach { note ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(note.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                        IconButton(onClick = { viewModel.deleteClientNote(note.noteId) }) {
                                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(note.content, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Zapisano: " + DateUtils.getRelativeTimeSpanString(note.timestamp).toString(),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Real-Time voice/video calling overlay
            showCallOverlay?.let { callType ->
                var speakerOn by remember { mutableStateOf(true) }
                var micMuted by remember { mutableStateOf(false) }
                var cameraOff by remember { mutableStateOf(false) }

                LaunchedEffect(callType) {
                    callDurationSeconds = 0
                    while (true) {
                        delay(1000)
                        callDurationSeconds++
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.94f))
                        .clickable(enabled = true, onClick = {}) // block touch interactions
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top row indicating call type
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (callType == "VOICE") Icons.Default.Call else Icons.Default.Videocam,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (callType == "VOICE") "SZYFROWANE POŁĄCZENIE GŁOSOWE" else "SZYFROWANE POŁĄCZENIE WIDEO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }

                        // Center content (Avatar with green pulsing visual or simulated camera feed)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(160.dp)
                            ) {
                                if (!cameraOff) {
                                    // Simulated pulse ring
                                    Box(
                                        modifier = Modifier
                                            .size(140.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(110.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                    )
                                    // Avatar itself
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = otherUser?.firstName?.firstOrNull()?.toString()?.uppercase() ?: "?",
                                            color = Color.Black,
                                            fontSize = 36.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    // Camera turned off visual placeholder
                                    Box(
                                        modifier = Modifier
                                            .size(140.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VideocamOff,
                                            contentDescription = "Camera turned off",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = otherUser?.let { "${it.firstName} ${it.lastName}" } ?: "Ładowanie...",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Call timer
                            val minutes = callDurationSeconds / 60
                            val seconds = callDurationSeconds % 60
                            Text(
                                text = "Rozmowa trwa: ${"%02d:%02d".format(minutes, seconds)}",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Action Controls (Speaker, Camera, Hang Up, Mute)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Speaker toggle
                            IconButton(
                                onClick = { speakerOn = !speakerOn },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(if (speakerOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (speakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                    contentDescription = "Głośnik",
                                    tint = if (speakerOn) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }

                            // Camera toggle
                            IconButton(
                                onClick = { cameraOff = !cameraOff },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(if (!cameraOff) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (!cameraOff) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    contentDescription = "Kamera",
                                    tint = if (!cameraOff) MaterialTheme.colorScheme.primary else Color(0xFFEF4444)
                                )
                            }

                            // Hang Up Red Button
                            IconButton(
                                onClick = { showCallOverlay = null },
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color(0xFFEF4444), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Rozłącz się",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Mute toggle
                            IconButton(
                                onClick = { micMuted = !micMuted },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(if (micMuted) Color(0xFFEF4444).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (micMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Wycisz",
                                    tint = if (micMuted) Color(0xFFEF4444) else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageEntity, isOutgoing: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOutgoing) 16.dp else 2.dp,
                bottomEnd = if (isOutgoing) 2.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isOutgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    color = if (isOutgoing) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ------------------ RATINGS / OPINIE SCREEN (For psychologist logged in) ------------------

@Composable
fun MyRatingsScreen(
    viewModel: WektorViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val reviewsState = if (currentUser != null) {
        viewModel.getReviewsForPsychologist(currentUser!!.id).collectAsStateWithLifecycle(emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    val reviews = reviewsState.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moje Oceny i Opinie") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(16.dp)
        ) {
            currentUser?.let { user ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Średnia Twoich Ocen", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "%.1f".format(user.rating),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row {
                            repeat(5) {
                                Icon(Icons.Default.Star, "Star", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Na podstawie ${reviews.size} opinii pacjentów", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }

                Text("Historia Opinii", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))

                if (reviews.isEmpty()) {
                    EmptyListPlaceholder(message = "Nie otrzymałeś jeszcze żadnych opinii.")
                } else {
                    reviews.forEach { rev ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(rev.reviewerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Row {
                                        repeat(rev.rating) {
                                            Icon(Icons.Default.Star, "Star", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(rev.comment, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = DateUtils.getRelativeTimeSpanString(rev.timestamp).toString(),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------ MOJE CZATY / KLIENCI LIST ------------------

@Composable
fun MyClientsScreen(
    viewModel: WektorViewModel,
    onBack: () -> Unit
) {
    val chats by viewModel.activeChats.collectAsStateWithLifecycle()
    val participants by viewModel.chatParticipants.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isPsych = currentUser?.role == "PSYCHOLOGIST" || currentUser?.role == "STUDENT"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPsych) "Moje czaty i klienci" else "Moje czaty i psycholodzy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrót")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (chats.isEmpty()) {
            EmptyListPlaceholder(message = "Brak aktywnych konwersacji pomocowych.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(chats) { chat ->
                    // Resolve other user
                    val currentUser = viewModel.currentUser.value
                    val otherId = if (chat.psychologistId == currentUser?.id) chat.patientId else chat.psychologistId
                    val otherUser = participants[otherId]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateTo(Screen.ChatRoom(chat.chatId)) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(otherUser?.firstName?.firstOrNull()?.toString() ?: "?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = otherUser?.let { "${it.firstName} ${it.lastName}" } ?: "Rozmówca",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = chat.lastMessage.ifEmpty { "Rozpocznij konwersację..." },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 1
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = DateUtils.getRelativeTimeSpanString(chat.lastMessageTime).toString(),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                // Custom badge to mark "ZAMETKI" as requested in screenshot 8 mojeklienci
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Notatki", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------ PROFILE EDIT SCREEN ------------------

@Composable
fun ProfileEditScreen(
    viewModel: WektorViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
 
    var profileImageUriString by remember { mutableStateOf<String?>(null) }
    var bio by remember { mutableStateOf("") }
    var specializations by remember { mutableStateOf("") }
    var pricePerSessionString by remember { mutableStateOf("") }
 
    var showAddPriceDialog by remember { mutableStateOf(false) }
    var selectedServiceType by remember { mutableStateOf("Sesja audio") }
    var newPriceValueString by remember { mutableStateOf("") }
    var localPricesList by remember { mutableStateOf(listOf<Pair<String, Int>>()) }
 
    var showSpecSelectionDialog by remember { mutableStateOf(false) }
    var selectedSpecsSet by remember { mutableStateOf(setOf<String>()) }
    var tempSelectedSpecs by remember { mutableStateOf(setOf<String>()) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            profileImageUriString = it.toString()
        }
    }
 
    LaunchedEffect(currentUser) {
        currentUser?.let {
            profileImageUriString = it.profileImageUri
            bio = it.bio
            specializations = it.specializations
            selectedSpecsSet = it.specializations.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.toSet()
            pricePerSessionString = it.pricePerSession.toInt().toString()

            val rawPrices = it.customPrices ?: ""
            localPricesList = if (rawPrices.isBlank()) {
                emptyList()
            } else {
                rawPrices.split(";").mapNotNull { part ->
                    val pieces = part.split("-")
                    if (pieces.size == 2) {
                        val service = pieces[0].trim()
                        val price = pieces[1].replace("PLN", "").replace("WKT", "").trim().toIntOrNull() ?: 0
                        service to price
                    } else null
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edycja Profilu") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(16.dp)
        ) {
            // Big avatar circle with pencil icon on bottom-right
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.BottomEnd
            ) {
                if (!profileImageUriString.isNullOrEmpty()) {
                    AsyncImage(
                        model = profileImageUriString,
                        contentDescription = "Zdjęcie profilowe",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Avatar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }

                // Edit pencil button
                IconButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Zmień zdjęcie",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Your info text section
            Text(
                text = currentUser?.let { "${it.firstName} ${it.lastName}" } ?: "",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Text(
                text = when (currentUser?.role) {
                    "PSYCHOLOGIST" -> "Psycholog"
                    "STUDENT" -> "Student Psychologii"
                    else -> "Pacjent"
                } + " • ${currentUser?.age ?: 30} lat",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
            )
            
            Text(
                text = currentUser?.email ?: "",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp, bottom = 24.dp)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 24.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("O mnie / Opis zgłoszenia") },
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(bottom = 16.dp)
            )

            if (currentUser?.role == "PSYCHOLOGIST" || currentUser?.role == "STUDENT") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable {
                            tempSelectedSpecs = selectedSpecsSet
                            showSpecSelectionDialog = true
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
                                text = "Moje Specjalizacje",
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
                        
                        if (selectedSpecsSet.isEmpty()) {
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
                                selectedSpecsSet.forEach { spec ->
                                    SpecializationChip(
                                        text = spec,
                                        isSelected = true
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = pricePerSessionString,
                    onValueChange = { pricePerSessionString = it },
                    label = { Text("Cena domyślna sesji (WKT/h)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )

                // "Moje Ceny" Crud section
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Moje Ceny i Rodzaje Zajęć",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = {
                            selectedServiceType = "Sesja audio"
                            newPriceValueString = ""
                            showAddPriceDialog = true
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Dodaj cenę", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (localPricesList.isEmpty()) {
                    Text(
                        text = "Brak skonfigurowanych stawek niestandardowych. Kliknij ikonę plus, aby dodać stawkę za sesję audio/wideo lub powiadomienia.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        localPricesList.forEachIndexed { index, item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when {
                                                item.first.contains("wideo", ignoreCase = true) -> Icons.Default.Videocam
                                                item.first.contains("audio", ignoreCase = true) -> Icons.Default.Call
                                                else -> Icons.Default.Notifications
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(item.first, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text("${item.second} WKT / godzina", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            localPricesList = localPricesList.filterIndexed { idx, _ -> idx != index }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Pop-up dialog for adding a new service price
            if (showAddPriceDialog) {
                AlertDialog(
                    onDismissRequest = { showAddPriceDialog = false },
                    title = { Text("Dodaj Rodzaj Zajęć i Cenę", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("1. Wybierz rodzaj zajęć:", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            
                            listOf("Sesja audio", "Sesja wideo", "Powiadomienia terapeutyczne").forEach { type ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedServiceType = type }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedServiceType == type,
                                        onClick = { selectedServiceType = type }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(type)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("2. Wpisz stawkę w WKT (za godzinę):", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            OutlinedTextField(
                                value = newPriceValueString,
                                onValueChange = { newPriceValueString = it },
                                label = { Text("Cena w WKT") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val pr = newPriceValueString.toIntOrNull() ?: 100
                                localPricesList = localPricesList + (selectedServiceType to pr)
                                showAddPriceDialog = false
                            }
                        ) {
                            Text("Zatwierdź", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showAddPriceDialog = false }) {
                            Text("Anuluj")
                        }
                    }
                )
            }

            // Pop-up dialog for choosing specializations
            if (showSpecSelectionDialog) {
                AlertDialog(
                    onDismissRequest = { showSpecSelectionDialog = false },
                    title = { 
                        Text(
                            "Wybierz Specjalizacje", 
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
                                "Zaznacz specjalizacje, które posiadasz. Kliknij na odpowiednie kółka, aby się podświetliły.",
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
                                    val isSelected = tempSelectedSpecs.contains(specName)
                                    SpecializationChip(
                                        text = specName,
                                        isSelected = isSelected,
                                        onClick = {
                                            tempSelectedSpecs = if (isSelected) {
                                                tempSelectedSpecs - specName
                                            } else {
                                                tempSelectedSpecs + specName
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
                                selectedSpecsSet = tempSelectedSpecs
                                specializations = tempSelectedSpecs.joinToString(", ")
                                showSpecSelectionDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Zatwierdź", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSpecSelectionDialog = false }) {
                            Text("Anuluj", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val formattedPricesString = localPricesList.joinToString(";") { "${it.first} - ${it.second} WKT" }
                    viewModel.updateProfile(
                        firstName = currentUser?.firstName ?: "",
                        lastName = currentUser?.lastName ?: "",
                        age = currentUser?.age ?: 30,
                        gender = currentUser?.gender ?: "Kobieta",
                        bio = bio,
                        specializations = specializations,
                        pricePerSession = pricePerSessionString.toDoubleOrNull() ?: 0.0,
                        customPrices = formattedPricesString,
                        profileImageUri = profileImageUriString
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Zapisz Zmiany", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

// ------------------ FAQ SCREEN ------------------

@Composable
fun FAQScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FAQ i Zalety Wektora") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(16.dp)
        ) {
            FAQItem(
                title = "Wektor: Zalety dla pacjenta",
                content = "- Introwertycy i osoby lękowe mogą łatwiej opowiedzieć o swoich problemach online niż sam na sam na żywo.\n- Szybkie i lekkie znajdowanie psychologa niezależnie od miejsca pobytu pacjenta.\n- Dobór psychologa idealnie pod swoje kryteria (wiek, płeć, specjalizacje, cena, opinie).\n- Niższe wydatki na psychoterapię dzięki modelowi łączącemu również studentów i doktorantów."
            )

            FAQItem(
                title = "Wektor: Zalety dla psychologów i studentów",
                content = "- Łatwość w odnajdywaniu nowych klientów w całej Polsce i za granicą.\n- Możliwość elastycznego dopasowania godzin pracy całkowicie pod siebie.\n- Praca całkowicie zdalna, bez konieczności tracenia czasu na dojazdy.\n- Pomoc studentom psychologii w poznaniu realnych sytuacji z klientami, nabieranie niezbędnego doświadczenia pod okiem uczelni oraz wykorzystanie teorii w bezpiecznej praktyce."
            )

            FAQItem(
                title = "Wektor: Współpraca z Uniwersytetami",
                content = "- Ułatwiamy uniwersytetom organizowanie obowiązkowych praktyk zawodowych dla studentów wyższych roczników psychologii w bezpiecznym środowisku cyfrowym."
            )

            FAQItem(
                title = "Jak przebiega weryfikacja dokumentów?",
                content = "Zgodnie z planem aplikacji, każdy psycholog chcący uzyskać status zweryfikowany musi zeskanować za pomocą naszego wbudowanego skanera dowód tożsamości, a następnie swój dyplom wyższej uczelni lub posiadane certyfikaty. Po automatycznej weryfikacji przy nazwisku pojawia się zielona plakietka zaufania."
            )
        }
    }
}

@Composable
fun FAQItem(title: String, content: String) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            AnimatedVisibility(visible = expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = content,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
        }
    }
}

// ------------------ WRITE OFFER FORM DIALOG ------------------

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

// ------------------ INTERACTIVE TUTORIAL OVERLAY ------------------

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
fun CalendarScreen(
    viewModel: WektorViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val chatParticipants by viewModel.chatParticipants.collectAsStateWithLifecycle()
    
    val isPsych = currentUser?.role == "PSYCHOLOGIST" || currentUser?.role == "STUDENT"
    val psychAppointments by viewModel.currentPsychologistAppointments.collectAsStateWithLifecycle()
    val patientAppointments by viewModel.currentPatientAppointments.collectAsStateWithLifecycle()
    
    var showAddSlotDialog by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var selectedHour by remember { mutableStateOf(12) }
    var selectedMinute by remember { mutableStateOf(0) }
    
    var showCancelConfirmDialog by remember { mutableStateOf<com.example.data.AppointmentEntity?>(null) }
    var showCompleteConfirmDialog by remember { mutableStateOf<com.example.data.AppointmentEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<com.example.data.AppointmentEntity?>(null) }
    
    // Auto-fill some defaults for tomorrow (July 10, 2026)
    LaunchedEffect(showAddSlotDialog) {
        if (showAddSlotDialog) {
            selectedDay = 10
            selectedHour = 12
            selectedMinute = 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kalendarz i Wizyty") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrót")
                    }
                },
                actions = {
                    if (isPsych) {
                        IconButton(onClick = { showAddSlotDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Dodaj termin", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if (isPsych) {
                FloatingActionButton(
                    onClick = { showAddSlotDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj wolny termin")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (currentUser == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Scaffold
            }

            // User Info Banner
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (isPsych) "Twój terminarz pracy" else "Twoje nadchodzące sesje",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isPsych) "Definiuj wolne godziny i kontroluj zapisy pacjentów" else "Przeglądaj harmonogram spotkań z psychologami",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            if (isPsych) {
                // PSYCHOLOGIST CALENDAR VIEW
                val bookedSlots = psychAppointments.count { it.status == "BOOKED" }
                val completedSlots = psychAppointments.count { it.status == "COMPLETED" }
                val freeSlots = psychAppointments.count { it.status == "FREE" }

                // Quick Statistics Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBox(modifier = Modifier.weight(1f), title = "Wolne", value = freeSlots.toString(), color = MaterialTheme.colorScheme.primary)
                    StatBox(modifier = Modifier.weight(1f), title = "Rezerwacje", value = bookedSlots.toString(), color = MaterialTheme.colorScheme.secondary)
                    StatBox(modifier = Modifier.weight(1f), title = "Zakończone", value = completedSlots.toString(), color = Color.Gray)
                }

                Text(
                    text = "Lista wszystkich terminów",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (psychAppointments.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventBusy, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Twój kalendarz jest pusty.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Kliknij przycisk +, aby dodać pierwsze wolne godziny.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(psychAppointments) { appointment ->
                            val patientUser = appointment.patientId?.let { chatParticipants[it] }
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = when (appointment.status) {
                                        "FREE" -> MaterialTheme.colorScheme.surface
                                        "BOOKED" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val dateParts = appointment.date.split("-")
                                            val displayDate = if (dateParts.size == 3) "${dateParts[2]}.${dateParts[1]}.${dateParts[0]}" else appointment.date
                                            Text(
                                                text = "$displayDate o ${appointment.time}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }

                                        // Status badge
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = when (appointment.status) {
                                                        "FREE" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                        "BOOKED" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                                                        else -> Color.Gray.copy(alpha = 0.2f)
                                                    },
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = when (appointment.status) {
                                                    "FREE" -> "Wolny"
                                                    "BOOKED" -> "Zarezerwowany"
                                                    else -> "Ukończony"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (appointment.status) {
                                                    "FREE" -> MaterialTheme.colorScheme.primary
                                                    "BOOKED" -> MaterialTheme.colorScheme.secondary
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }

                                    if (appointment.status == "BOOKED" && patientUser != null) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        
                                        Text(
                                            text = "Pacjent: ${patientUser.firstName} ${patientUser.lastName}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (appointment.notes.isNotEmpty()) {
                                            Text(
                                                text = "Opis problemu: ${appointment.notes}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { showCompleteConfirmDialog = appointment },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.weight(1f).height(38.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Zakończ", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            
                                            Button(
                                                onClick = { showCancelConfirmDialog = appointment },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                modifier = Modifier.weight(1f).height(38.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Odwołaj", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else if (appointment.status == "COMPLETED" && patientUser != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Pacjent: ${patientUser.firstName} ${patientUser.lastName} (Zakończona wizyta)",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    } else if (appointment.status == "FREE") {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            IconButton(
                                                onClick = { showDeleteConfirmDialog = appointment },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Usuń wolny slot",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // PATIENT CALENDAR VIEW
                val upcomingSessions = patientAppointments.filter { it.status == "BOOKED" }
                val pastSessions = patientAppointments.filter { it.status == "COMPLETED" }

                Text(
                    text = "Nadchodzące spotkania (${upcomingSessions.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (upcomingSessions.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.EventBusy, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Brak zaplanowanych wizyt.", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Wejdź w profil wybranego psychologa z listy głównej i zarezerwuj dogodny wolny termin.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(upcomingSessions) { appointment ->
                            val psychUser = chatParticipants[appointment.psychologistId]
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val dateParts = appointment.date.split("-")
                                            val displayDate = if (dateParts.size == 3) "${dateParts[2]}.${dateParts[1]}.${dateParts[0]}" else appointment.date
                                            Text("$displayDate o ${appointment.time}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("Potwierdzona", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    if (psychUser != null) {
                                        Text("Psycholog: ${psychUser.firstName} ${psychUser.lastName}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("Specjalizacje: ${psychUser.specializations}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    } else {
                                        Text("Psycholog (użytkownik id: ${appointment.psychologistId})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                    
                                    if (appointment.notes.isNotEmpty()) {
                                        Text(
                                            text = "Twój opis: ${appointment.notes}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { showCancelConfirmDialog = appointment },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.fillMaxWidth().height(40.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Odwołaj rezerwację", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // History / Past Sessions
                if (pastSessions.isNotEmpty()) {
                    Text(
                        text = "Historia sesji (${pastSessions.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(0.7f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pastSessions) { appointment ->
                            val psychUser = chatParticipants[appointment.psychologistId]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        val dateParts = appointment.date.split("-")
                                        val displayDate = if (dateParts.size == 3) "${dateParts[2]}.${dateParts[1]}.${dateParts[0]}" else appointment.date
                                        Text("$displayDate o ${appointment.time}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = if (psychUser != null) "Psycholog: ${psychUser.firstName} ${psychUser.lastName}" else "Ukończona sesja",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .background(Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Zrealizowano", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // 1. ADD SLOT DIALOG
    if (showAddSlotDialog) {
        val formattedDateCheck = selectedDay?.let { "2026-07-${it.toString().padStart(2, '0')}" } ?: ""
        val formattedTimeCheck = "${selectedHour.toString().padStart(2, '0')}:${selectedMinute.toString().padStart(2, '0')}"
        val isAlreadyBookedOrFree = selectedDay != null && psychAppointments.any { 
            it.date == formattedDateCheck && it.time == formattedTimeCheck
        }

        AlertDialog(
            onDismissRequest = { showAddSlotDialog = false },
            title = { 
                Text(
                    text = "Dodaj wolny termin",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Wybierz dzień z kalendarza na lipiec 2026 r., a następnie ustaw godzinę sesji.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    
                    // 1. Calendar
                    July2026Calendar(
                        selectedDay = selectedDay,
                        onDaySelected = { day ->
                            selectedDay = day
                        }
                    )
                    
                    // 2. Animated Time Picker (Visible only after day is chosen)
                    AnimatedVisibility(
                        visible = selectedDay != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Wybierz godzinę i minutę:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            
                            WheelTimePicker(
                                selectedHour = selectedHour,
                                selectedMinute = selectedMinute,
                                onTimeSelected = { hour, minute ->
                                    selectedHour = hour
                                    selectedMinute = minute
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Selection Summary
                            selectedDay?.let { day ->
                                if (isAlreadyBookedOrFree) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Masz już dodany lub zaplanowany termin o ${selectedHour.toString().padStart(2, '0')}:${selectedMinute.toString().padStart(2, '0')} w tym dniu!",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                } else {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Wybrany termin: ${day.toString().padStart(2, '0')}.07.2026 o ${selectedHour.toString().padStart(2, '0')}:${selectedMinute.toString().padStart(2, '0')}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val day = selectedDay
                        if (day != null && !isAlreadyBookedOrFree) {
                            val formattedDate = "2026-07-${day.toString().padStart(2, '0')}"
                            val formattedTime = "${selectedHour.toString().padStart(2, '0')}:${selectedMinute.toString().padStart(2, '0')}"
                            viewModel.addFreeSlot(formattedDate, formattedTime)
                            showAddSlotDialog = false
                        }
                    },
                    enabled = selectedDay != null && !isAlreadyBookedOrFree,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                ) {
                    Text("Dodaj do grafiku", color = if (selectedDay != null && !isAlreadyBookedOrFree) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSlotDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }

    // 2. CANCEL CONFIRM DIALOG
    if (showCancelConfirmDialog != null) {
        val app = showCancelConfirmDialog!!
        
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = null },
            title = { Text("Potwierdź odwołanie wizyty") },
            text = {
                val dateParts = app.date.split("-")
                val displayDate = if (dateParts.size == 3) "${dateParts[2]}.${dateParts[1]}.${dateParts[0]}" else app.date
                Text(
                    text = "Czy na pewno chcesz odwołać wizytę zaplanowaną na $displayDate o godzinie ${app.time}?\n\n" +
                           "Status wizyty zostanie cofnięty do wolnego slotu, a pacjent zostanie powiadomiony.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelAppointmentBooking(app.appointmentId)
                        showCancelConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Odwołaj wizytę", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = null }) {
                    Text("Anuluj")
                }
            }
        )
    }

    // 3. COMPLETE CONFIRM DIALOG
    if (showCompleteConfirmDialog != null) {
        val app = showCompleteConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showCompleteConfirmDialog = null },
            title = { Text("Oznacz jako zrealizowaną") },
            text = {
                Text("Czy chcesz oznaczyć tę sesję jako pomyślnie zrealizowaną? Spowoduje to przeniesienie wizyty do historii.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.completeAppointment(app.appointmentId)
                        showCompleteConfirmDialog = null
                    }
                ) {
                    Text("Zrealizowano", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteConfirmDialog = null }) {
                    Text("Anuluj")
                }
            }
        )
    }

    // 4. DELETE FREE SLOT CONFIRM DIALOG
    if (showDeleteConfirmDialog != null) {
        val app = showDeleteConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Usuń wolny termin") },
            text = {
                val dateParts = app.date.split("-")
                val displayDate = if (dateParts.size == 3) "${dateParts[2]}.${dateParts[1]}.${dateParts[0]}" else app.date
                Text("Czy na pewno chcesz usunąć wolny termin $displayDate o ${app.time} ze swojego kalendarza?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAppointmentSlot(app.appointmentId)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Usuń", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Anuluj")
                }
            }
        )
    }
}

@Composable
fun StatBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun July2026Calendar(
    selectedDay: Int?,
    onDaySelected: (day: Int) -> Unit
) {
    val daysOfWeek = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "Sb", "Nd")
    
    // July 1st, 2026 is a Wednesday (Środa), which is the 3rd day of the week.
    // So we need 2 leading empty slots.
    val totalDays = 31
    val startingEmptySlots = 2
    val today = 9 // July 9th, 2026
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // Calendar Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lipiec 2026",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Krok 1: Wybierz dzień",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        // Days of week header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            daysOfWeek.forEach { dayName ->
                Text(
                    text = dayName,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        
        // Days Grid (6 rows max)
        val gridItemsCount = totalDays + startingEmptySlots
        val rows = (gridItemsCount + 6) / 7
        
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (c in 0 until 7) {
                    val index = r * 7 + c
                    val dayNum = index - startingEmptySlots + 1
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (index >= startingEmptySlots && dayNum <= totalDays) {
                            val isActive = dayNum >= today
                            val isSelected = selectedDay == dayNum
                            
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            dayNum == today -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                            else -> Color.Transparent
                                        },
                                        shape = CircleShape
                                    )
                                    .clickable(enabled = isActive) {
                                        onDaySelected(dayNum)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayNum.toString(),
                                    fontWeight = if (isSelected || dayNum == today) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> Color.Black
                                        isActive -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                    },
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WheelTimePicker(
    selectedHour: Int,
    selectedMinute: Int,
    onTimeSelected: (hour: Int, minute: Int) -> Unit
) {
    val hours = (0..23).map { it.toString().padStart(2, '0') }
    val minutes = (0..59 step 5).map { it.toString().padStart(2, '0') }
    
    val hoursListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedHour)
    val minutesListState = rememberLazyListState(initialFirstVisibleItemIndex = minutes.indexOf(selectedMinute.toString().padStart(2, '0')).coerceAtLeast(0))
    
    val coroutineScope = rememberCoroutineScope()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hours column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text("Godzina", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            
            Box(
                modifier = Modifier
                    .height(110.dp)
                    .width(80.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background highlight for the center item
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                )
                
                LazyColumn(
                    state = hoursListState,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = hoursListState),
                    contentPadding = PaddingValues(vertical = 38.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(hours) { index, hour ->
                        val isSelected = hoursListState.firstVisibleItemIndex == index
                        
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        hoursListState.animateScrollToItem(index)
                                        val mIdx = minutesListState.firstVisibleItemIndex.coerceIn(0, minutes.size - 1)
                                        onTimeSelected(index, minutes[mIdx].toInt())
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = hour,
                                fontSize = if (isSelected) 18.sp else 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
        
        // Separator ":"
        Text(
            text = ":",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        
        // Minutes column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text("Minuta", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            
            Box(
                modifier = Modifier
                    .height(110.dp)
                    .width(80.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background highlight for the center item
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                )
                
                LazyColumn(
                    state = minutesListState,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = minutesListState),
                    contentPadding = PaddingValues(vertical = 38.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(minutes) { index, minute ->
                        val isSelected = minutesListState.firstVisibleItemIndex == index
                        
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        minutesListState.animateScrollToItem(index)
                                        val hIdx = hoursListState.firstVisibleItemIndex.coerceIn(0, hours.size - 1)
                                        onTimeSelected(hIdx, minute.toInt())
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = minute,
                                fontSize = if (isSelected) 18.sp else 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Sync selection when scroll finishes
    LaunchedEffect(hoursListState.isScrollInProgress, minutesListState.isScrollInProgress) {
        if (!hoursListState.isScrollInProgress && !minutesListState.isScrollInProgress) {
            val hIdx = hoursListState.firstVisibleItemIndex.coerceIn(0, hours.size - 1)
            val mIdx = minutesListState.firstVisibleItemIndex.coerceIn(0, minutes.size - 1)
            onTimeSelected(hIdx, minutes[mIdx].toInt())
        }
    }
}

@Composable
fun WalletScreen(
    viewModel: WektorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val transactions by viewModel.walletTransactions.collectAsStateWithLifecycle()
    
    // UI states
    var showPacksDialog by remember { mutableStateOf(false) }
    
    // Dialog / Overlay controls
    var showCheckoutDialog by remember { mutableStateOf<CoinPack?>(null) }
    var selectedPaymentMethod by remember { mutableStateOf("BLIK") }
    var checkoutCode by remember { mutableStateOf("") }
    var showSuccessOverlay by remember { mutableStateOf(false) }
    var lastAddedCoins by remember { mutableStateOf(0) }
    
    var showTransferDialog by remember { mutableStateOf(false) }
    var transferRecipient by remember { mutableStateOf("") }
    var transferAmount by remember { mutableStateOf("") }
    var showTransferSuccess by remember { mutableStateOf(false) }
    
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var withdrawAddress by remember { mutableStateOf("") }
    var withdrawAmount by remember { mutableStateOf("") }
    var showWithdrawSuccess by remember { mutableStateOf(false) }
    
    var showDetailsMenu by remember { mutableStateOf(false) }

    val coinPacks = listOf(
        CoinPack("Pakiet Mały", 50, "50,00 PLN", "Idealny na start i przetestowanie wizyty"),
        CoinPack("Pakiet Standardowy", 100, "100,00 PLN", "Najbardziej popularny pakiet"),
        CoinPack("Pakiet Profesjonalny", 250, "250,00 PLN", "Najlepsza wartość i wygoda")
    )

    // Dark-themed Telegram colors
    val bgMain = Color(0xFF0F0F10)
    val bgCard = Color(0xFF1C1D22)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFF8E8E93)
    val telegramBlue = Color(0xFF24A1DE)
    val coinGold = Color(0xFFFFD700)
    val positiveGreen = Color(0xFF2E7D32)
    val errorRed = Color(0xFFE53935)

    val isPsychologist = currentUser?.role == "PSYCHOLOGIST" || currentUser?.role == "STUDENT"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgMain
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Premium Telegram App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Close",
                        color = textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "Portfel",
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                IconButton(
                    onClick = { showDetailsMenu = !showDetailsMenu }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Więcej",
                        tint = textPrimary
                    )
                }
            }

            // Profile row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left avatar circle
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3B3C42)),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = currentUser?.firstName?.take(1) ?: "U"
                    Text(
                        text = initial,
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // Right Scanner/QR Icon
                IconButton(
                    onClick = {
                        android.widget.Toast.makeText(context, "Skaner kodów QR zostanie uruchomiony wkrótce!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .background(bgCard, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Skanuj QR",
                        tint = textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {

                // Main Balance Display
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${currentUser?.coinsBalance ?: 0}",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WKT",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = coinGold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // 1:1 estimate (1 WKT = 1 PLN)
                    val plnValue = (currentUser?.coinsBalance ?: 0).toDouble()
                    Text(
                        text = "~ ${String.format("%.2f", plnValue)} PLN",
                        fontSize = 15.sp,
                        color = textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Quick Action Buttons Underneath Balance
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Deposit
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showPacksDialog = true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(bgCard, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Doładuj",
                                tint = textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Deposit", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    // Withdraw
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showWithdrawDialog = true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(bgCard, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Wypłać",
                                tint = textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Withdraw", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ledger history
                Text(
                    text = "Historia portfela",
                    color = textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (transactions.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = bgCard)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Brak transakcji w historii",
                                color = textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    transactions.forEach { tx ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = bgCard)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (tx.amount > 0) positiveGreen.copy(alpha = 0.15f)
                                            else errorRed.copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (tx.amount > 0) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = if (tx.amount > 0) positiveGreen else errorRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.title,
                                        color = textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    val dateStr = DateUtils.getRelativeTimeSpanString(
                                        tx.timestamp,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS
                                    ).toString()
                                    Text(
                                        text = dateStr,
                                        color = textSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    text = if (tx.amount > 0) "+${tx.amount} WKT" else "${tx.amount} WKT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (tx.amount > 0) positiveGreen else errorRed
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action pop-ups & Dialogs
        
        // 1. Dropdown Info Details
        if (showDetailsMenu) {
            AlertDialog(
                onDismissRequest = { showDetailsMenu = false },
                title = { Text("O Portfelu WKT", color = textPrimary) },
                text = {
                    Text(
                        text = "System portfela monet pozwala Ci bezpiecznie kupować i przesyłać monety Wektor (WKT). Dzięki monetom rezerwujesz wizyty u psychologów bez podawania danych bankowych przy każdej transakcji.",
                        color = textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showDetailsMenu = false }) {
                        Text("Rozumiem", color = telegramBlue)
                    }
                },
                containerColor = bgCard
            )
        }

        // 2. Buy Coins (Deposit)
        if (showCheckoutDialog != null) {
            val pack = showCheckoutDialog!!
            AlertDialog(
                onDismissRequest = { showCheckoutDialog = null },
                title = { Text("Doładuj Portfel Monet", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Wybrany pakiet: ${pack.name}", color = textPrimary, fontWeight = FontWeight.Bold)
                        Text("Dodaje do konta: +${pack.coins} monet WKT", color = coinGold, fontWeight = FontWeight.SemiBold)
                        Text("Cena: ${pack.price}", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Metoda płatności:", color = textSecondary, fontSize = 12.sp)
                        
                        listOf("BLIK", "Karta płatnicza", "Przelew bankowy").forEach { method ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        if (selectedPaymentMethod == method) telegramBlue.copy(alpha = 0.1f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (selectedPaymentMethod == method) telegramBlue else textSecondary.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedPaymentMethod = method }
                                    .padding(10.dp)
                            ) {
                                RadioButton(
                                    selected = selectedPaymentMethod == method,
                                    onClick = { selectedPaymentMethod = method },
                                    colors = RadioButtonDefaults.colors(selectedColor = telegramBlue, unselectedColor = textSecondary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(method, color = textPrimary, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (selectedPaymentMethod == "BLIK") {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = checkoutCode,
                                onValueChange = { checkoutCode = it },
                                label = { Text("Kod BLIK (6 cyfr)", color = textSecondary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = telegramBlue,
                                    unfocusedBorderColor = textSecondary.copy(alpha = 0.3f),
                                    focusedTextColor = textPrimary,
                                    unfocusedTextColor = textPrimary
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.topUpWallet(pack.coins)
                            lastAddedCoins = pack.coins
                            showCheckoutDialog = null
                            checkoutCode = ""
                            showSuccessOverlay = true
                        },
                        enabled = selectedPaymentMethod != "BLIK" || checkoutCode.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = telegramBlue)
                    ) {
                        Text("Zapłać i doładuj", color = textPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCheckoutDialog = null }) {
                        Text("Anuluj", color = textSecondary)
                    }
                },
                containerColor = bgCard
            )
        }

        // Success Buy Overlay
        if (showSuccessOverlay) {
            Dialog(onDismissRequest = { showSuccessOverlay = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = bgCard)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = positiveGreen,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Doładowanie udane! 🎉",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Portfel został zasilony kwotą +$lastAddedCoins WKT.",
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showSuccessOverlay = false },
                            colors = ButtonDefaults.buttonColors(containerColor = telegramBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Super!", color = textPrimary)
                        }
                    }
                }
            }
        }



        // 4. Transfer Coins Dialog
        if (showTransferDialog) {
            AlertDialog(
                onDismissRequest = { showTransferDialog = false },
                title = { Text("Przelej monety WKT", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Prześlij monety natychmiastowo do innego użytkownika za pomocą jego adresu lub pseudonimu.", color = textSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = transferRecipient,
                            onValueChange = { transferRecipient = it },
                            label = { Text("Adres odbiorcy (np. UQDy...)", color = textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = telegramBlue,
                                unfocusedBorderColor = textSecondary.copy(alpha = 0.3f),
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = transferAmount,
                            onValueChange = { transferAmount = it },
                            label = { Text("Ilość monet WKT", color = textSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = telegramBlue,
                                unfocusedBorderColor = textSecondary.copy(alpha = 0.3f),
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amount = transferAmount.toIntOrNull() ?: 0
                            if (amount <= 0) {
                                android.widget.Toast.makeText(context, "Wpisz poprawną kwotę!", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (transferRecipient.isBlank()) {
                                android.widget.Toast.makeText(context, "Wpisz adres odbiorcy!", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val success = viewModel.spendCoins(amount, "Przelew do: $transferRecipient")
                            if (success) {
                                showTransferDialog = false
                                showTransferSuccess = true
                                transferAmount = ""
                                transferRecipient = ""
                            } else {
                                android.widget.Toast.makeText(context, "Brak wystarczających środków!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = telegramBlue)
                    ) {
                        Text("Wyślij przelew", color = textPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTransferDialog = false }) {
                        Text("Anuluj", color = textSecondary)
                    }
                },
                containerColor = bgCard
            )
        }

        // Transfer Success Overlay
        if (showTransferSuccess) {
            Dialog(onDismissRequest = { showTransferSuccess = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = bgCard)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = positiveGreen,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Przelew wysłany! 🚀",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Monety zostały natychmiastowo przelane na adres odbiorcy.",
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showTransferSuccess = false },
                            colors = ButtonDefaults.buttonColors(containerColor = telegramBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Świetnie!", color = textPrimary)
                        }
                    }
                }
            }
        }

        // 5. Withdraw Coins Dialog
        if (showWithdrawDialog) {
            AlertDialog(
                onDismissRequest = { showWithdrawDialog = false },
                title = { Text("Wypłać monety WKT", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Przelej monety na konto bankowe lub tradycyjną kartę płatniczą. Minimalna kwota wypłaty: 75 monet (75 PLN). Przelicznik: 1 WKT = 1.00 PLN.", color = textSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = withdrawAddress,
                            onValueChange = { withdrawAddress = it },
                            label = { Text("Numer konta (IBAN) lub karty", color = textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = telegramBlue,
                                unfocusedBorderColor = textSecondary.copy(alpha = 0.3f),
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = withdrawAmount,
                            onValueChange = { withdrawAmount = it },
                            label = { Text("Ilość monet WKT do wypłaty (min. 75)", color = textSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = telegramBlue,
                                unfocusedBorderColor = textSecondary.copy(alpha = 0.3f),
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            )
                        )

                        val amountToWithdraw = withdrawAmount.toIntOrNull() ?: 0
                        if (amountToWithdraw > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Otrzymasz przelew na kwotę: ${String.format("%.2f", amountToWithdraw.toDouble())} PLN",
                                color = coinGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amount = withdrawAmount.toIntOrNull() ?: 0
                            if (amount < 75) {
                                android.widget.Toast.makeText(context, "Minimalna kwota wypłaty to 75 WKT (75 PLN)!", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (withdrawAddress.isBlank()) {
                                android.widget.Toast.makeText(context, "Wpisz numer konta/karty!", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val success = viewModel.spendCoins(amount, "Wypłata środków (WKT -> PLN)")
                            if (success) {
                                showWithdrawDialog = false
                                showWithdrawSuccess = true
                                withdrawAmount = ""
                                withdrawAddress = ""
                            } else {
                                android.widget.Toast.makeText(context, "Brak wystarczających środków!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = telegramBlue)
                    ) {
                        Text("Zleć wypłatę", color = textPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWithdrawDialog = false }) {
                        Text("Anuluj", color = textSecondary)
                    }
                },
                containerColor = bgCard
            )
        }

        // Withdraw Success Overlay
        if (showWithdrawSuccess) {
            Dialog(onDismissRequest = { showWithdrawSuccess = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = bgCard)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = positiveGreen,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Zlecenie wypłaty przyjęte! 💳",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Środki zostaną zaksięgowane na Twoim koncie w ciągu 1-2 dni roboczych.",
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showWithdrawSuccess = false },
                            colors = ButtonDefaults.buttonColors(containerColor = telegramBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Rozumiem", color = textPrimary)
                        }
                    }
                }
            }
        }

        // Packs Selection Dialog (Deposit)
        if (showPacksDialog) {
            AlertDialog(
                onDismissRequest = { showPacksDialog = false },
                title = { Text("Doładuj Portfel (Pakiety)", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        coinPacks.forEach { pack ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showCheckoutDialog = pack
                                        showPacksDialog = false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = bgMain)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(coinGold.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Paid,
                                            contentDescription = null,
                                            tint = coinGold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${pack.name} (+${pack.coins} WKT)",
                                            color = textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = pack.description,
                                            color = textSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(telegramBlue, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = pack.price,
                                            color = textPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showPacksDialog = false }) {
                        Text("Zamknij", color = textSecondary)
                    }
                },
                containerColor = bgCard
            )
        }
    }
}

data class CoinPack(
    val name: String,
    val coins: Int,
    val price: String,
    val description: String
)

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


