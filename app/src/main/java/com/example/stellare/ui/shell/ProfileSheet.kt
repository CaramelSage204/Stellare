package com.example.stellare.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.chat.ChatViewModel
import com.example.stellare.ui.feed.FeedViewModel
import com.example.stellare.ui.navigation.Routes
import com.example.stellare.ui.profile.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    feedViewModel: FeedViewModel,
    profileViewModel: ProfileViewModel,
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val currentUser by profileViewModel.currentUser.collectAsStateWithLifecycle()
    val favoriteUsers by feedViewModel.favoritePsychologists.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    val isPsycholog = currentUser?.role == UserRole.PSYCHOLOGIST ||
        currentUser?.role == UserRole.PSYCHOLOGY_STUDENT

    fun zamknijZAnimacja() {
        coroutineScope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
            }
        }
    }

    fun nawigujIZamknij(route: String) {
        onNavigate(route)
        zamknijZAnimacja()
    }

    ModalBottomSheet(sheetState = sheetState, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            ProfileSheetHeader(currentUser)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            ProfileSheetItem(
                icon = Icons.Filled.Person,
                label = "Mój profil",
                onClick = { nawigujIZamknij(Routes.PROFILE_EDIT) }
            )

            if (isPsycholog) {
                ProfileSheetItem(
                    icon = Icons.Filled.Star,
                    label = "Moje oceny",
                    onClick = { nawigujIZamknij(Routes.MY_RATINGS) }
                )
                ProfileSheetItem(
                    icon = Icons.Filled.People,
                    label = "Moi klienci",
                    onClick = { nawigujIZamknij(Routes.MY_CLIENTS) }
                )
                ProfileSheetItem(
                    icon = Icons.Filled.Badge,
                    label = "Weryfikacja kwalifikacji",
                    onClick = { nawigujIZamknij(Routes.VERIFICATION) }
                )
            }

            ProfileSheetItem(
                icon = Icons.Filled.HelpOutline,
                label = "Zalety i FAQ Stellare",
                onClick = { nawigujIZamknij(Routes.FAQ) }
            )

            if (currentUser?.isAdmin == true) {
                ProfileSheetItem(
                    icon = Icons.Filled.VerifiedUser,
                    label = "Panel weryfikacji",
                    onClick = { nawigujIZamknij(Routes.VERIFICATION_ADMIN) }
                )
            }

            if (favoriteUsers.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    text = if (isPsycholog) "Ulubieni pacjenci" else "Ulubieni psycholodzy",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                favoriteUsers.forEach { favoriteUser ->
                    FavoriteUserRow(
                        user = favoriteUser,
                        isPsycholog = isPsycholog,
                        onClick = {
                            if (isPsycholog) {
                                coroutineScope.launch {
                                    val chatId = chatViewModel.openChatWith(favoriteUser.id)
                                    if (chatId != null) {
                                        nawigujIZamknij(Routes.chatRoom(chatId))
                                    }
                                }
                            } else {
                                nawigujIZamknij(Routes.psychologistDetail(favoriteUser.id))
                            }
                        },
                        onToggleFavorite = { feedViewModel.toggleFavorite(favoriteUser.id) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            ProfileSheetItem(
                icon = Icons.Filled.ExitToApp,
                label = "Wyloguj",
                tint = MaterialTheme.colorScheme.error,
                onClick = {
                    authViewModel.logout()
                    zamknijZAnimacja()
                }
            )
        }
    }
}

@Composable
private fun ProfileSheetHeader(currentUser: UserModel?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!currentUser?.profileImageUri.isNullOrEmpty()) {
            AsyncImage(
                model = currentUser?.profileImageUri,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentUser?.firstName?.firstOrNull()?.toString()?.uppercase() ?: "?",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${currentUser?.firstName.orEmpty()} ${currentUser?.lastName.orEmpty()}".trim()
                        .ifEmpty { "Użytkownik" },
                    fontWeight = FontWeight.Bold
                )
                if (currentUser?.isVerified == true) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Zweryfikowany",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = when (currentUser?.role) {
                    UserRole.PSYCHOLOGIST -> "Psycholog"
                    UserRole.PSYCHOLOGY_STUDENT -> "Student Psychologii"
                    else -> "Pacjent"
                },
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
                        imageVector = Icons.Filled.Paid,
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
}

@Composable
private fun ProfileSheetItem(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FavoriteUserRow(
    user: UserModel,
    isPsycholog: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!user.profileImageUri.isNullOrEmpty()) {
            AsyncImage(
                model = user.profileImageUri,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
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
                    text = user.firstName.firstOrNull()?.toString()?.uppercase() ?: "?",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${user.firstName} ${user.lastName}",
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isPsycholog) {
                    "Pacjent • ${user.age} lat"
                } else {
                    user.specializations.split(",").firstOrNull()?.trim().takeUnless { it.isNullOrEmpty() }
                        ?: "Psycholog"
                },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Usuń z ulubionych",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
