package com.example.stellare.ui.psychologistdetail

import android.text.format.DateUtils
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.ui.StellareViewModel
import com.example.stellare.ui.components.SpecializationChip
import com.example.stellare.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PsychologistDetailScreen(
    viewModel: StellareViewModel,
    psychologistId: String,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var psych by remember { mutableStateOf<UserModel?>(null) }
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
                            label = specName,
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
                                Text("Certyfikat_Zweryfikowany_Przez_Stellare", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.Default.Visibility, "View", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Real time slots booking
                val availableSlots by viewModel.getFreeSlotsForPsychologist(psychologistId).collectAsStateWithLifecycle(emptyList())
                var selectedAppointment by remember { mutableStateOf<AppointmentModel?>(null) }
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
                            Text("Zweryfikowany Dyplom Stellare", fontWeight = FontWeight.Bold)
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
