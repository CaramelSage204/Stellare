package com.example.stellare.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.model.UserModel
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.chat.ChatViewModel
import com.example.stellare.ui.feed.FeedViewModel
import com.example.stellare.ui.posts.PostViewModel
import kotlinx.coroutines.launch

private data class PostFeedTab(
    val label: String,
    val posts: List<PostModel>,
    val emptyMessage: String
)

@Composable
fun MainDashboardScreen(
    feedViewModel: FeedViewModel,
    postViewModel: PostViewModel,
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    currentUser: UserModel?,
    onOpenProfileSheet: () -> Unit,
    onPsychologistSelected: (String) -> Unit,
    onChatOpened: (String) -> Unit,
    onShowChats: () -> Unit,
    onNewPost: () -> Unit
) {
    val filters by feedViewModel.filters.collectAsStateWithLifecycle()
    val searchQuery = filters.query
    val coroutineScope = rememberCoroutineScope()

    val offeringPosts by postViewModel.offeringPosts.collectAsStateWithLifecycle()
    val seekingPosts by postViewModel.seekingPosts.collectAsStateWithLifecycle()

    var showFiltersSheet by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var selectedFeedTab by remember { mutableIntStateOf(0) }
    var activeTutorialStep by remember { mutableIntStateOf(0) }
    val showTutorialPrompt by authViewModel.showTutorialPrompt.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenProfileSheet) {
                        val profileImageUri = currentUser?.profileImageUri
                        if (!profileImageUri.isNullOrEmpty()) {
                            AsyncImage(
                                model = profileImageUri,
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stellare",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onNewPost, modifier = Modifier.testTag("new_post_button")) {
                        Icon(Icons.Default.Add, contentDescription = "Napisz ogłoszenie", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showNotificationsDialog = true }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Powiadomienia", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onShowChats) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Wiadomości")
                    }
                }

                // Search Bar and Filters trigger Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { feedViewModel.updateQuery(it) },
                        placeholder = { Text("Wyszukaj po imieniu, frazie...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Szukaj") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { feedViewModel.updateQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = { showFiltersSheet = !showFiltersSheet },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = if (showFiltersSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filtry",
                            tint = if (showFiltersSheet) Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { feedViewModel.resetFilters() },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Odśwież",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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
            // Tutorial Invitation Dialog Prompt
            if (showTutorialPrompt) {
                AlertDialog(
                    onDismissRequest = { authViewModel.dismissTutorialPrompt() },
                    title = { Text("Czy chcesz przejść szybki samouczek?") },
                    text = { Text("Dziękujemy za rejestrację w Stellare! Chcesz dowiedzieć się, jak otwierać swoje konto, pisać oferty, znajdować psychologów lub pacjentów, pisać notatki terapeutyczne oraz ustalić stawkę?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                authViewModel.dismissTutorialPrompt()
                                activeTutorialStep = 1
                            }
                        ) {
                            Text("Tak, jasne!", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { authViewModel.dismissTutorialPrompt() }
                        ) {
                            Text("Pomiń", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                )
            }



            // Real System Notifications Dialog
            if (showNotificationsDialog) {
                AlertDialog(
                    onDismissRequest = { showNotificationsDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Centrum Powiadomień", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Magdalena Nowak zaakceptowała Twój czat pomocowy.", fontSize = 13.sp)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Stellare: Twoje kwalifikacje zostały pomyślnie zweryfikowane!", fontSize = 13.sp)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Gray))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Witamy na platformie Stellare! Życzymy udanego dnia.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showNotificationsDialog = false }) {
                            Text("Zamknij")
                        }
                    }
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Filter dropdown expansion card if triggered
                AnimatedVisibility(visible = showFiltersSheet) {
                    FiltersSelectionCard(
                        viewModel = feedViewModel,
                        onDismiss = { showFiltersSheet = false }
                    )
                }

                val isPsychologist = currentUser?.role == UserRole.PSYCHOLOGIST || currentUser?.role == UserRole.PSYCHOLOGY_STUDENT

                val searchedOfferingPosts = offeringPosts.filter { filters.matchesPost(it) }
                val searchedSeekingPosts = seekingPosts.filter { filters.matchesPost(it) }

                val postFeedTabs = if (isPsychologist) {
                    listOf(
                        PostFeedTab(
                            label = "Szukam psychologa",
                            posts = searchedOfferingPosts,
                            emptyMessage = "Brak dostępnych ogłoszeń psychologów spełniających kryteria."
                        ),
                        PostFeedTab(
                            label = "Szukam pacjenta",
                            posts = searchedSeekingPosts,
                            emptyMessage = "Brak zgłoszeń od pacjentów w tym momencie."
                        )
                    )
                } else {
                    listOf(
                        PostFeedTab(
                            label = "Szukam psychologa",
                            posts = searchedOfferingPosts,
                            emptyMessage = "Brak dostępnych ogłoszeń psychologów spełniających kryteria."
                        )
                    )
                }

                val activeTabIndex = selectedFeedTab.coerceIn(0, postFeedTabs.lastIndex)
                val activeTab = postFeedTabs[activeTabIndex]

                if (postFeedTabs.size > 1) {
                    TabRow(selectedTabIndex = activeTabIndex) {
                        postFeedTabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = index == activeTabIndex,
                                onClick = { selectedFeedTab = index },
                                text = { Text(tab.label) }
                            )
                        }
                    }
                }

                if (activeTab.posts.isEmpty()) {
                    EmptyListPlaceholder(message = activeTab.emptyMessage)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(activeTab.posts, key = { it.id }) { post ->
                            PostCard(
                                post = post,
                                onClick = {
                                    when (post.type) {
                                        PostType.OFFERING_THERAPY -> onPsychologistSelected(post.authorId)
                                        PostType.SEEKING_PSYCHOLOGIST -> coroutineScope.launch {
                                            chatViewModel.openChatWith(post.authorId)?.let { onChatOpened(it) }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

        // Interactive Tutorial Overlay
        if (activeTutorialStep > 0) {
            StellareTutorialOverlay(
                step = activeTutorialStep,
                currentUser = currentUser,
                onNext = {
                    if (activeTutorialStep < 5) {
                        activeTutorialStep += 1
                    } else {
                        activeTutorialStep = 0
                    }
                },
                onSkip = {
                    activeTutorialStep = 0
                }
            )
        }
    }
}
