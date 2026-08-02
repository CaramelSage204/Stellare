package com.example.ui.chat

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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
import com.example.data.local.UserRole
import com.example.data.local.entity.ChatEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.UserEntity
import com.example.ui.WektorViewModel
import com.example.ui.components.EmptyListPlaceholder
import com.example.ui.navigation.Screen
import kotlinx.coroutines.delay

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

                // Tabs - Chat vs Zametki
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
                // ZAMETKI (NOTES) LAYOUT
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
                        .clickable(enabled = true, onClick = {}) 
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

                        // Center content
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

                        // Action Controls
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
                    color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyClientsScreen(
    viewModel: WektorViewModel,
    onBack: () -> Unit
) {
    val chats by viewModel.activeChats.collectAsStateWithLifecycle()
    val participants by viewModel.chatParticipants.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isPsych = currentUser?.role == UserRole.PSYCHOLOGIST || currentUser?.role == UserRole.PSYCHOLOGY_STUDENT

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
                                    text = android.text.format.DateUtils.getRelativeTimeSpanString(chat.lastMessageTime).toString(),
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

