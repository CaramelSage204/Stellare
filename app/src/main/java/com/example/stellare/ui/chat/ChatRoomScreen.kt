package com.example.stellare.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.data.model.ChatModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.settings.BubbleColorChoice
import com.example.stellare.ui.RelativeTime
import com.example.stellare.ui.components.EmptyListPlaceholder
import com.example.stellare.ui.profile.ProfileViewModel
import java.util.TimeZone

@Composable
fun ChatRoomScreen(
    viewModel: ChatViewModel,
    profileViewModel: ProfileViewModel,
    chatId: String,
    bubbleColor: BubbleColorChoice,
    onBack: () -> Unit
) {
    val messages by viewModel.messagesFor(chatId).collectAsStateWithLifecycle(emptyList())
    val notes by viewModel.notesFor(chatId).collectAsStateWithLifecycle(emptyList())
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val currentUser by profileViewModel.currentUser.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose { viewModel.clearActionError() }
    }

    val rows = remember(messages, currentUser?.id) {
        MessageGrouping.rowsFor(messages, currentUser?.id, TimeZone.getDefault())
    }
    val rowKeys = remember(rows) { MessageGrouping.keysFor(rows) }

    var requestedTab by rememberSaveable { mutableStateOf("CHAT") }

    var chatSession by remember { mutableStateOf<ChatModel?>(null) }
    var otherUser by remember { mutableStateOf<UserModel?>(null) }

    LaunchedEffect(chatId, chats, participants, currentUser) {
        val chat = chats.find { it.chatId == chatId }
        chatSession = chat
        val currUser = currentUser
        if (chat != null && currUser != null) {
            val otherId = if (chat.psychologistId == currUser.id) chat.patientId else chat.psychologistId
            otherUser = participants[otherId]
        }
    }

    val showNotesTab = canSeeNotesTab(chatSession, currentUser?.id)
    val chatTab = if (showNotesTab) requestedTab else "CHAT"

    var textInput by rememberSaveable { mutableStateOf("") }

    var noteTitleInput by rememberSaveable { mutableStateOf("") }
    var noteContentInput by rememberSaveable { mutableStateOf("") }
    var isAddingNote by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
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
                    }
                }

                if (showNotesTab) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { requestedTab = "CHAT" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (chatTab == "CHAT") MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            shape = RoundedCornerShape(0.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val tabContent = if (chatTab == "CHAT") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            Icon(Icons.Default.ChatBubble, contentDescription = "Rozmowa", tint = tabContent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Rozmowa", color = tabContent, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { requestedTab = "NOTES" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (chatTab == "NOTES") MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            shape = RoundedCornerShape(0.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val tabContent = if (chatTab == "NOTES") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            Icon(Icons.Default.NoteAlt, contentDescription = "Notatki", tint = tabContent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Notatki", color = tabContent, fontWeight = FontWeight.Bold)
                        }
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
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        reverseLayout = false,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(rows, key = { index, _ -> rowKeys[index] }) { _, row ->
                            when (row) {
                                is ChatRow.DateSeparator -> DateSeparatorLabel(row.label)
                                is ChatRow.Bubble -> MessageBubble(
                                    message = row.message,
                                    isOutgoing = row.isOutgoing,
                                    isFirstOfGroup = row.isFirstOfGroup,
                                    isLastOfGroup = row.isLastOfGroup,
                                    bubbleColor = bubbleColor
                                )
                            }
                        }
                    }

                    ChatActionError(actionError)

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
                                    viewModel.send(chatId, textInput)
                                    textInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Wyślij", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            } else {
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
                                contentDescription = "Dodaj notatkę",
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
                                Text("Nowa notatka", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
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
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(bottom = 16.dp)
                                )
                                Button(
                                    onClick = {
                                        if (noteTitleInput.isNotEmpty() && noteContentInput.isNotEmpty()) {
                                            viewModel.saveNote(chatId, noteTitleInput, noteContentInput)
                                            noteTitleInput = ""
                                            noteContentInput = ""
                                            isAddingNote = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Zapisz Notatkę", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    ChatActionError(actionError)

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
                                        IconButton(onClick = { viewModel.deleteNote(note.noteId) }) {
                                            Icon(Icons.Default.Delete, "Usuń", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(note.content, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Zapisano: " + RelativeTime.format(note.timestamp, viewModel.nowMillis()),
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
}

@Composable
private fun ChatActionError(message: String?) {
    if (message == null) return
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}
