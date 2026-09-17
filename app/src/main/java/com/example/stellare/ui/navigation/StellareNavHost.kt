package com.example.stellare.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.stellare.di.AppContainer
import com.example.stellare.di.stellareViewModelFactory
import com.example.stellare.ui.auth.AuthStatus
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.calendar.CalendarScreen
import com.example.stellare.ui.calendar.CalendarViewModel
import com.example.stellare.ui.chat.ChatRoomScreen
import com.example.stellare.ui.chat.ChatViewModel
import com.example.stellare.ui.chat.MyClientsScreen
import com.example.stellare.ui.dashboard.MainDashboardScreen
import com.example.stellare.ui.feed.FeedViewModel
import com.example.stellare.ui.posts.PostComposerScreen
import com.example.stellare.ui.posts.PostViewModel
import com.example.stellare.ui.profile.FAQScreen
import com.example.stellare.ui.profile.ProfileEditScreen
import com.example.stellare.ui.profile.ProfileViewModel
import com.example.stellare.ui.psychologistdetail.PsychologistDetailScreen
import com.example.stellare.ui.reviews.MyRatingsScreen
import com.example.stellare.ui.session.LoginScreen
import com.example.stellare.ui.session.OnboardingScreen
import com.example.stellare.ui.session.RegistrationScreen
import com.example.stellare.ui.session.SplashScreen
import com.example.stellare.ui.verification.AdminPanelScreen
import com.example.stellare.ui.verification.VerificationScreen
import com.example.stellare.ui.verification.VerificationViewModel
import com.example.stellare.ui.wallet.WalletScreen
import com.example.stellare.ui.wallet.WalletViewModel

@Composable
fun StellareNavHost(
    navController: NavHostController,
    container: AppContainer,
    onOpenProfileSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val factory = remember(container) { stellareViewModelFactory(container) }
    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val feedViewModel: FeedViewModel = viewModel(factory = factory)
    val calendarViewModel: CalendarViewModel = viewModel(factory = factory)
    val walletViewModel: WalletViewModel = viewModel(factory = factory)
    val chatViewModel: ChatViewModel = viewModel(factory = factory)
    val profileViewModel: ProfileViewModel = viewModel(factory = factory)
    val postViewModel: PostViewModel = viewModel(factory = factory)
    val verificationViewModel: VerificationViewModel = viewModel(factory = factory)

    val authStatus by authViewModel.authStatus.collectAsStateWithLifecycle()
    val currentUser by profileViewModel.currentUser.collectAsStateWithLifecycle()

    LaunchedEffect(authStatus) {
        when (authStatus) {
            AuthStatus.LOGGED_IN -> navController.navigate(Routes.FEED) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
            AuthStatus.LOGGED_OUT -> navController.navigate(Routes.ONBOARDING) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
            AuthStatus.UNKNOWN -> Unit
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier
    ) {
        composable(Routes.SPLASH) {
            SplashScreen()
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onNavigateToRegister = { navController.navigate(Routes.REGISTRATION) },
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.REGISTRATION) {
            RegistrationScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.FEED) {
            MainDashboardScreen(
                feedViewModel = feedViewModel,
                postViewModel = postViewModel,
                authViewModel = authViewModel,
                chatViewModel = chatViewModel,
                currentUser = currentUser,
                onOpenProfileSheet = onOpenProfileSheet,
                onPsychologistSelected = { psychologistId ->
                    navController.navigate(Routes.psychologistDetail(psychologistId))
                },
                onChatOpened = { chatId -> navController.navigate(Routes.chatRoom(chatId)) },
                onShowChats = { navController.navigate(Routes.CHATS) },
                onNewPost = { navController.navigate(Routes.POST_COMPOSER) }
            )
        }

        composable(Routes.POST_COMPOSER) {
            PostComposerScreen(
                viewModel = postViewModel,
                profileViewModel = profileViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CALENDAR) {
            CalendarScreen(
                viewModel = calendarViewModel,
                profileViewModel = profileViewModel,
                chatViewModel = chatViewModel,
                onOpenProfileSheet = onOpenProfileSheet,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CHATS) {
            MyClientsScreen(
                viewModel = chatViewModel,
                profileViewModel = profileViewModel,
                onOpenProfileSheet = onOpenProfileSheet,
                onBack = { navController.popBackStack() },
                onChatSelected = { chatId -> navController.navigate(Routes.chatRoom(chatId)) }
            )
        }

        composable(Routes.WALLET) {
            WalletScreen(
                viewModel = walletViewModel,
                profileViewModel = profileViewModel,
                onOpenProfileSheet = onOpenProfileSheet,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PROFILE_EDIT) {
            ProfileEditScreen(
                viewModel = profileViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MY_RATINGS) {
            MyRatingsScreen(
                viewModel = profileViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MY_CLIENTS) {
            MyClientsScreen(
                viewModel = chatViewModel,
                profileViewModel = profileViewModel,
                onOpenProfileSheet = onOpenProfileSheet,
                onBack = { navController.popBackStack() },
                onChatSelected = { chatId -> navController.navigate(Routes.chatRoom(chatId)) }
            )
        }

        composable(Routes.FAQ) {
            FAQScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.VERIFICATION) {
            VerificationScreen(
                viewModel = verificationViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.VERIFICATION_ADMIN) {
            AdminPanelScreen(
                viewModel = verificationViewModel,
                profileViewModel = profileViewModel,
                isAdmin = currentUser?.isAdmin == true,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PSYCHOLOGIST_DETAIL,
            arguments = listOf(navArgument("psychologistId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("psychologistId").orEmpty()

            PsychologistDetailScreen(
                profileViewModel = profileViewModel,
                feedViewModel = feedViewModel,
                calendarViewModel = calendarViewModel,
                chatViewModel = chatViewModel,
                psychologistId = id,
                onBack = { navController.popBackStack() },
                onNavigateToWallet = { navController.navigate(Routes.WALLET) },
                onNavigateToCalendar = { navController.navigate(Routes.CALENDAR) },
                onChatOpened = { chatId -> navController.navigate(Routes.chatRoom(chatId)) }
            )
        }

        composable(
            route = Routes.CHAT_ROOM,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("chatId").orEmpty()

            ChatRoomScreen(
                viewModel = chatViewModel,
                profileViewModel = profileViewModel,
                chatId = id,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
