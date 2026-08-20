package com.example.stellare.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.StellareViewModel
import com.example.stellare.ui.calendar.views.PatientCalendarView
import com.example.stellare.ui.calendar.views.PsychologistCalendarView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: StellareViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val chatParticipants by viewModel.chatParticipants.collectAsStateWithLifecycle()

    val isPsych = currentUser?.role == UserRole.PSYCHOLOGIST || currentUser?.role == UserRole.PSYCHOLOGY_STUDENT
    val psychAppointments by viewModel.currentPsychologistAppointments.collectAsStateWithLifecycle()
    val patientAppointments by viewModel.currentPatientAppointments.collectAsStateWithLifecycle()

    var showAddSlotDialog by remember { mutableStateOf(false) }

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
                PsychologistCalendarView(
                    appointments = psychAppointments,
                    participants = chatParticipants,
                    showAddSlotDialog = showAddSlotDialog,
                    onAddSlotDismiss = { showAddSlotDialog = false },
                    onAddSlotConfirm = { date, time ->
                        viewModel.addFreeSlot(date, time)
                        showAddSlotDialog = false
                    },
                    onCompleteAppointment = { viewModel.completeAppointment(it) },
                    onCancelAppointment = { viewModel.cancelAppointmentBooking(it) },
                    onDeleteAppointment = { viewModel.deleteAppointmentSlot(it) }
                )
            } else {
                PatientCalendarView(
                    appointments = patientAppointments,
                    participants = chatParticipants,
                    onCancelAppointment = { viewModel.cancelAppointmentBooking(it) }
                )
            }
        }
    }
}