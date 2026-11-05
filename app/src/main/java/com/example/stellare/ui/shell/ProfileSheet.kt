package com.example.stellare.ui.shell

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.chat.ChatViewModel
import com.example.stellare.ui.feed.FeedViewModel
import com.example.stellare.ui.navigation.Routes
import com.example.stellare.ui.components.StaleDataNotice
import com.example.stellare.ui.profile.ProfileViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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
    val profileReadFailure by profileViewModel.profileReadFailure.collectAsStateWithLifecycle()
    val privateProfile by profileViewModel.privateProfile.collectAsStateWithLifecycle()
    val favoriteUsers by feedViewModel.favoritePsychologists.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    val verticalInsetsDp = systemBars.calculateTopPadding().value + systemBars.calculateBottomPadding().value
    val contentMaxHeight = ProfileSheetLayout.contentMaxHeightDp(screenHeightDp, verticalInsetsDp).dp

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
                .heightIn(max = contentMaxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            StaleDataNotice(message = profileReadFailure, onRetry = profileViewModel::retryProfile)

            ProfileSheetHeader(currentUser, privateProfile)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            ProfileSheetItem(
                icon = Icons.Filled.Person,
                label = "Mój profil",
                onClick = { nawigujIZamknij(Routes.PROFILE_EDIT) }
            )

            ProfileSheetItem(
                icon = Icons.Filled.Phone,
                label = "Numer telefonu",
                onClick = { nawigujIZamknij(Routes.PHONE_VERIFICATION) }
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

            ProfileSheetItem(
                icon = Icons.Filled.Settings,
                label = "Ustawienia",
                onClick = { nawigujIZamknij(Routes.SETTINGS) }
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
                    text = ProfileSheetPresentation.favoritesHeading(isPsycholog),
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
