package com.example.ui.calendar

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.WektorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    
    var showCancelConfirmDialog by remember { mutableStateOf<com.example.data.local.entity.AppointmentEntity?>(null) }
    var showCompleteConfirmDialog by remember { mutableStateOf<com.example.data.local.entity.AppointmentEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<com.example.data.local.entity.AppointmentEntity?>(null) }
    
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
