package com.example.stellare.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.components.StaleDataNotice
import com.example.stellare.ui.ListKeys
import com.example.stellare.ui.RelativeTime
import com.example.stellare.ui.components.EmptyListPlaceholder
import com.example.stellare.ui.profile.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyClientsScreen(
    viewModel: ChatViewModel,
    profileViewModel: ProfileViewModel,
    onOpenProfileSheet: () -> Unit,
    onBack: (() -> Unit)?,
    onChatSelected: (String) -> Unit
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val chatKeys = remember(chats) { ListKeys.unique(chats.map { it.chatId }) }
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val currentUser by profileViewModel.currentUser.collectAsStateWithLifecycle()
    val profileReadFailure by profileViewModel.profileReadFailure.collectAsStateWithLifecycle()
    val isPsych = currentUser?.role == UserRole.PSYCHOLOGIST || currentUser?.role == UserRole.PSYCHOLOGY_STUDENT

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPsych) "Moje czaty i klienci" else "Moje czaty i psycholodzy", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrót")
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        StaleDataNotice(
            message = profileReadFailure,
            onRetry = profileViewModel::retryProfile,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (chats.isEmpty()) {
            EmptyListPlaceholder(message = "Brak aktywnych konwersacji pomocowych.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(chats, key = { index, _ -> chatKeys[index] }) { _, chat ->
                    val otherId = if (chat.psychologistId == currentUser?.id) chat.patientId else chat.psychologistId
                    val otherUser = participants[otherId]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChatSelected(chat.chatId) },
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
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = chat.lastMessage.ifEmpty { "Rozpocznij konwersację..." },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = RelativeTime.format(chat.lastMessageTime, viewModel.nowMillis()),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (canSeeNotesTab(chat, currentUser?.id)) {
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
    }
}
