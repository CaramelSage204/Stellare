package com.example.stellare.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.components.StaleDataNotice
import com.example.stellare.ui.profile.ProfileViewModel
import java.util.TimeZone
import com.example.stellare.ui.calendar.views.PatientCalendarView
import com.example.stellare.ui.calendar.views.PsychologistCalendarView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    profileViewModel: ProfileViewModel,
    onOpenProfileSheet: () -> Unit
) {
    val currentUser by profileViewModel.currentUser.collectAsStateWithLifecycle()
    val profileReadFailure by profileViewModel.profileReadFailure.collectAsStateWithLifecycle()
    val participantNames by viewModel.participantNames.collectAsStateWithLifecycle()
    val selectedDayMillis by viewModel.selectedDayMillis.collectAsStateWithLifecycle()
    val zone = remember { TimeZone.getDefault() }
    val todayMillis = rememberTodayMillis(viewModel::nowMillis, zone)
    val currentSelectedDay by rememberUpdatedState(selectedDayMillis)
    var previousTodayMillis by remember { mutableLongStateOf(selectedDayMillis) }

    LaunchedEffect(todayMillis) {
        val followed = CalendarDays.followToday(currentSelectedDay, previousTodayMillis, todayMillis, zone)
        previousTodayMillis = todayMillis
        if (followed != currentSelectedDay) viewModel.selectDay(followed)
    }

    val isPsych = currentUser?.role == UserRole.PSYCHOLOGIST || currentUser?.role == UserRole.PSYCHOLOGY_STUDENT
    val psychAppointments by viewModel.psychologistAppointments.collectAsStateWithLifecycle()
    val patientAppointments by viewModel.patientAppointments.collectAsStateWithLifecycle()

    var showAddSlotDialog by rememberSaveable { mutableStateOf(false) }
    val transferState by viewModel.transferState.collectAsStateWithLifecycle()
    val appointmentsStale by viewModel.appointmentsStaleMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(transferState) {
        val message = when (val state = transferState) {
            is TransferState.Cancelled -> RefundMessages.afterCancel(state.outcome, viewerIsPsychologist = isPsych)
            is TransferState.Reclaimed -> RefundMessages.afterReclaim(state.outcome)
            is TransferState.Completed -> RefundMessages.afterComplete(state.outcome)
            is TransferState.Failed -> state.message
            else -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.resetTransferState()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Kalendarz i Wizyty", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onOpenProfileSheet) {
                            val profileImageUri = currentUser?.profileImageUri
                            if (!profileImageUri.isNullOrEmpty()) {
                                AsyncImage(
                                    model = profileImageUri,
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
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
                    contentColor = MaterialTheme.colorScheme.onPrimary
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
                if (profileReadFailure != null) {
                    StaleDataNotice(message = profileReadFailure, onRetry = profileViewModel::retryProfile)
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                return@Scaffold
            }

            StaleDataNotice(message = profileReadFailure, onRetry = profileViewModel::retryProfile)
            StaleDataNotice(message = appointmentsStale, onRetry = viewModel::retryAppointments)

            if (isPsych) {
                PsychologistCalendarView(
                    appointments = psychAppointments,
                    userId = currentUser?.id,
                    participantNames = participantNames,
                    selectedDayMillis = selectedDayMillis,
                    todayMillis = todayMillis,
                    zone = zone,
                    nowProvider = viewModel::nowMillis,
                    showAddSlotDialog = showAddSlotDialog,
                    onSelectDay = viewModel::selectDay,
                    onAddSlotDismiss = { showAddSlotDialog = false },
                    onAddSlotConfirm = { startsAtMillis ->
                        viewModel.addFreeSlot(startsAtMillis)
                        showAddSlotDialog = false
                    },
                    onCompleteAppointment = { viewModel.complete(it) },
                    depositOf = viewModel::depositOf,
                    onCancelAppointment = { viewModel.cancel(it) },
                    onDeleteAppointment = { viewModel.deleteSlot(it) }
                )
            } else {
                PatientCalendarView(
                    appointments = patientAppointments,
                    userId = currentUser?.id,
                    participantNames = participantNames,
                    selectedDayMillis = selectedDayMillis,
                    todayMillis = todayMillis,
                    zone = zone,
                    nowProvider = viewModel::nowMillis,
                    onSelectDay = viewModel::selectDay,
                    depositOf = viewModel::depositOf,
                    onCancelAppointment = { viewModel.cancel(it) },
                    onReclaimAppointment = { viewModel.reclaim(it) }
                )
            }
        }
    }
}