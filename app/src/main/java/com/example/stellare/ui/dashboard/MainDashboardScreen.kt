package com.example.stellare.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.stellare.ui.components.StaleDataNotice
import com.example.stellare.ui.components.dialogTextScroll
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.model.UserModel
import com.example.stellare.ui.ListKeys
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.chat.ChatViewModel
import com.example.stellare.ui.components.EmptyListPlaceholder
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
    profileReadFailure: String?,
    onRetryProfile: () -> Unit,
    onOpenProfileSheet: () -> Unit,
    onPsychologistSelected: (String) -> Unit,
    onChatOpened: (String) -> Unit,
    onShowChats: () -> Unit,
    onNewPost: () -> Unit,
    onVerifyPhone: () -> Unit
) {
    val filters by feedViewModel.filters.collectAsStateWithLifecycle()
    val searchQuery = filters.query
    val coroutineScope = rememberCoroutineScope()

    val offeringPosts by postViewModel.offeringPosts.collectAsStateWithLifecycle()
    val seekingPosts by postViewModel.seekingPosts.collectAsStateWithLifecycle()
    val postsStale by postViewModel.postsStaleMessage.collectAsStateWithLifecycle()
    val ratingBadges by feedViewModel.authorRatingBadges.collectAsStateWithLifecycle()
    val ratingsStale by feedViewModel.ratingsStaleMessage.collectAsStateWithLifecycle()

    var showFiltersSheet by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var selectedFeedTab by rememberSaveable { mutableIntStateOf(0) }
    var activeTutorialStep by remember { mutableIntStateOf(0) }
    val showTutorialPrompt by authViewModel.showTutorialPrompt.collectAsStateWithLifecycle()
    val phoneVerificationOffer by authViewModel.phoneVerificationOffer.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            DashboardTopBar(
                currentUser = currentUser,
                searchQuery = searchQuery,
                filtersOpen = showFiltersSheet,
                onOpenProfileSheet = onOpenProfileSheet,
                onNewPost = onNewPost,
                onShowNotifications = { showNotificationsDialog = true },
                onShowChats = onShowChats,
                onQueryChange = feedViewModel::updateQuery,
                onToggleFilters = { showFiltersSheet = !showFiltersSheet },
                onResetFilters = feedViewModel::resetFilters
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (showTutorialPrompt) {
                TutorialPromptDialog(
                    onStart = {
                        authViewModel.dismissTutorialPrompt()
                        activeTutorialStep = 1
                    },
                    onSkip = authViewModel::dismissTutorialPrompt
                )
            }

            val offeredPhone = phoneVerificationOffer
            if (offeredPhone != null && !showTutorialPrompt && activeTutorialStep == 0) {
                PhoneVerificationOfferDialog(
                    phone = offeredPhone,
                    onConfirm = onVerifyPhone,
                    onLater = authViewModel::dismissPhoneVerificationOffer
                )
            }

            if (showNotificationsDialog) {
                NotificationsDialog(onDismiss = { showNotificationsDialog = false })
            }

            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = showFiltersSheet) {
                    FiltersSelectionCard(
                        viewModel = feedViewModel,
                        onDismiss = { showFiltersSheet = false }
                    )
                }

                StaleDataNotice(
                    message = profileReadFailure,
                    onRetry = onRetryProfile,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                    StaleDataNotice(
                    message = postsStale,
                    onRetry = postViewModel::retryPosts,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                StaleDataNotice(
                    message = ratingsStale,
                    onRetry = feedViewModel::retryRatings,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

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
                    val accentedId = accentedPostId(activeTab.posts)
                    val postKeys = remember(activeTab.posts) { ListKeys.unique(activeTab.posts.map { it.id }) }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(activeTab.posts, key = { index, _ -> postKeys[index] }) { _, post ->
                            PostCard(
                                post = post,
                                ratingText = ratingBadges[post.authorId],
                                isAccented = post.id == accentedId,
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
