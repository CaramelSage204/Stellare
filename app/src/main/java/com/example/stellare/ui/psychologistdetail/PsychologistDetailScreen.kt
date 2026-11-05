package com.example.stellare.ui.psychologistdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.data.model.UserModel
import com.example.stellare.ui.calendar.BookingState
import com.example.stellare.ui.calendar.CalendarViewModel
import com.example.stellare.ui.calendar.ConfirmationTarget
import com.example.stellare.ui.calendar.ConfirmationTarget.Operation
import com.example.stellare.ui.chat.ChatViewModel
import com.example.stellare.ui.components.StaleDataNotice
import com.example.stellare.ui.feed.FeedViewModel
import com.example.stellare.ui.profile.ProfileViewModel
import com.example.stellare.ui.profile.ReviewSubmitState
import com.example.stellare.ui.psychologistdetail.components.AboutSection
import com.example.stellare.ui.psychologistdetail.components.BookingActions
import com.example.stellare.ui.psychologistdetail.components.BookingConfirmDialog
import com.example.stellare.ui.psychologistdetail.components.BookingSuccessDialog
import com.example.stellare.ui.psychologistdetail.components.DetailHeader
import com.example.stellare.ui.psychologistdetail.components.FreeSlotsSection
import com.example.stellare.ui.psychologistdetail.components.ReviewsSection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsychologistDetailScreen(
    profileViewModel: ProfileViewModel,
    feedViewModel: FeedViewModel,
    calendarViewModel: CalendarViewModel,
    chatViewModel: ChatViewModel,
    psychologistId: String,
    onBack: () -> Unit,
    onNavigateToWallet: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onChatOpened: (String) -> Unit
) {
    val currentUser by profileViewModel.currentUser.collectAsStateWithLifecycle()
    val privateProfile by profileViewModel.privateProfile.collectAsStateWithLifecycle()
    var psych by remember { mutableStateOf<ProfileLoad>(ProfileLoad.Loading) }
    var profileReloads by remember { mutableIntStateOf(0) }
    val reviewsLoad by remember(psychologistId) { profileViewModel.reviewsLoadFor(psychologistId) }
        .collectAsStateWithLifecycle<ReviewsLoad?>(null)
    val reviews = (reviewsLoad as? ReviewsLoad.Loaded)?.reviews.orEmpty()
    val reviewSummary = remember(reviewsLoad) { ReviewSummary.fromLoad(reviewsLoad) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(psychologistId, profileReloads) {
        psych = ProfileLoad.Loading
        psych = profileViewModel.userById(psychologistId)
    }

    val reviewState by profileViewModel.reviewState.collectAsStateWithLifecycle()
    var hasCompletedSession by remember { mutableStateOf(false) }
    LaunchedEffect(psychologistId, currentUser?.id) {
        hasCompletedSession = currentUser != null && profileViewModel.hasCompletedSessionWith(psychologistId)
    }
    LaunchedEffect(reviewState) {
        if (reviewState is ReviewSubmitState.Success) {
            psych = profileViewModel.userById(psychologistId)
            profileViewModel.resetReviewState()
        }
    }
    DisposableEffect(psychologistId) {
        onDispose { profileViewModel.resetReviewState() }
    }

    val availableSlots by remember(psychologistId) { calendarViewModel.freeSlotsFor(psychologistId) }
        .collectAsStateWithLifecycle(emptyList())
    var selectedAppointmentId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedAppointment = ConfirmationTarget.resolve(selectedAppointmentId, availableSlots, Operation.BOOK)
    var showBookingDialog by rememberSaveable { mutableStateOf(false) }
    var bookingNotes by rememberSaveable { mutableStateOf("") }
    var bookingSuccess by remember { mutableStateOf(false) }
    val bookingState by calendarViewModel.bookingState.collectAsStateWithLifecycle()

    LaunchedEffect(bookingState) {
        bookingNotes = BookingNotes.afterOutcome(bookingState, bookingNotes)
        if (bookingState is BookingState.Success) {
            showBookingDialog = false
            bookingSuccess = true
            calendarViewModel.resetBookingState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil Psychologa", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrót")
                    }
                },
                actions = {
                    val favoriteIds by feedViewModel.favoriteIds.collectAsStateWithLifecycle()
                    val isFav = favoriteIds.contains(psychologistId)
                    IconButton(onClick = { feedViewModel.toggleFavorite(psychologistId) }) {
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
        when (val load = psych) {
            ProfileLoad.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            ProfileLoad.Absent -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nie znaleziono tego profilu.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is ProfileLoad.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)
            ) {
                StaleDataNotice(message = load.message, onRetry = { profileReloads++ })
            }
            is ProfileLoad.Loaded -> {
                val user = load.user
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                DetailHeader(user = user, reviewSummary = reviewSummary)
                Spacer(modifier = Modifier.height(16.dp))
                AboutSection(user = user)
                FreeSlotsSection(
                    slots = availableSlots,
                    selectedId = selectedAppointment?.appointmentId,
                    onSelect = { selectedAppointmentId = it.appointmentId }
                )
                BookingActions(
                    canBook = selectedAppointment != null,
                    onStartChat = {
                        coroutineScope.launch {
                            chatViewModel.openChatWith(user.id)?.let { onChatOpened(it) }
                        }
                    },
                    onBook = { showBookingDialog = true }
                )
                ReviewsSection(
                    summary = reviewSummary,
                    reviews = reviews,
                    currentUserId = currentUser?.id,
                    hasCompletedSession = hasCompletedSession,
                    reviewState = reviewState,
                    nowMillis = calendarViewModel.nowMillis(),
                    onSubmitReview = { rating, comment -> profileViewModel.addReview(psychologistId, rating, comment) },
                    onRetry = { profileViewModel.retryReviews() }
                )
            }

            selectedAppointment?.let { appointment ->
                if (showBookingDialog) {
                    BookingConfirmDialog(
                        psychologistName = "${user.firstName} ${user.lastName}",
                        appointment = appointment,
                        balance = privateProfile?.coinsBalance ?: 0,
                        notes = bookingNotes,
                        bookingState = bookingState,
                        onNotesChange = { bookingNotes = it },
                        onConfirm = { calendarViewModel.book(appointment, bookingNotes) },
                        onTopUp = {
                            showBookingDialog = false
                            calendarViewModel.resetBookingState()
                            onNavigateToWallet()
                        },
                        onDismiss = {
                            showBookingDialog = false
                            calendarViewModel.resetBookingState()
                        }
                    )
                }
            }

            if (bookingSuccess) {
                BookingSuccessDialog(
                    onDismiss = {
                        bookingSuccess = false
                        selectedAppointmentId = null
                    },
                    onOpenCalendar = {
                        bookingSuccess = false
                        selectedAppointmentId = null
                        onNavigateToCalendar()
                    }
                )
            }
        }
        }
    }
}
