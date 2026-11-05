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
import com.example.stellare.data.settings.AppSettings
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
import com.example.stellare.ui.faq.FAQScreen
import com.example.stellare.ui.settings.AccountDeletionViewModel
import com.example.stellare.ui.settings.DeleteAccountScreen
import com.example.stellare.ui.settings.SettingsScreen
import com.example.stellare.ui.settings.SettingsViewModel
import com.example.stellare.ui.profile.ProfileEditScreen
import com.example.stellare.ui.profile.ProfileViewModel
import com.example.stellare.ui.psychologistdetail.PsychologistDetailScreen
import com.example.stellare.ui.reviews.MyRatingsScreen
import com.example.stellare.ui.session.CompleteProfileScreen
import com.example.stellare.ui.session.LoginScreen
import com.example.stellare.ui.session.OnboardingScreen
import com.example.stellare.ui.session.PhoneVerificationScreen
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
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val accountDeletionViewModel: AccountDeletionViewModel = viewModel(factory = factory)

    val authStatus by authViewModel.authStatus.collectAsStateWithLifecycle()
    val currentUser by profileViewModel.currentUser.collectAsStateWithLifecycle()
    val profileReadFailure by profileViewModel.profileReadFailure.collectAsStateWithLifecycle()

    LaunchedEffect(authStatus) {
        val target = AuthRedirect.target(authStatus, navController.currentDestination?.route)
        if (target != null) {
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
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

        composable(Routes.COMPLETE_PROFILE) {
            CompleteProfileScreen(viewModel = authViewModel)
        }

        composable(Routes.FEED) {
            MainDashboardScreen(
                feedViewModel = feedViewModel,
                postViewModel = postViewModel,
                authViewModel = authViewModel,
                chatViewModel = chatViewModel,
                currentUser = currentUser,
                profileReadFailure = profileReadFailure,
                onRetryProfile = profileViewModel::retryProfile,
                onOpenProfileSheet = onOpenProfileSheet,
                onPsychologistSelected = { psychologistId ->
                    navController.navigate(Routes.psychologistDetail(psychologistId))
                },
                onChatOpened = { chatId -> navController.navigate(Routes.chatRoom(chatId)) },
                onShowChats = { navController.navigate(Routes.CHATS) },
                onNewPost = { navController.navigate(Routes.POST_COMPOSER) },
                onVerifyPhone = { navController.navigate(Routes.PHONE_VERIFICATION) }
            )
        }

        composable(Routes.PHONE_VERIFICATION) {
            PhoneVerificationScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() }
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
                onOpenProfileSheet = onOpenProfileSheet
            )
        }

        composable(Routes.CHATS) {
            MyClientsScreen(
                viewModel = chatViewModel,
                profileViewModel = profileViewModel,
                onOpenProfileSheet = onOpenProfileSheet,
                onBack = null,
                onChatSelected = { chatId -> navController.navigate(Routes.chatRoom(chatId)) }
            )
        }

        composable(Routes.WALLET) {
            WalletScreen(
                viewModel = walletViewModel,
                profileViewModel = profileViewModel,
                onOpenProfileSheet = onOpenProfileSheet
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

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onOpenPhoneVerification = { navController.navigate(Routes.PHONE_VERIFICATION) },
                onOpenFaq = { navController.navigate(Routes.FAQ) },
                onOpenDeleteAccount = { navController.navigate(Routes.DELETE_ACCOUNT) }
            )
        }

        composable(Routes.DELETE_ACCOUNT) {
            DeleteAccountScreen(
                viewModel = accountDeletionViewModel,
                onBack = { navController.popBackStack() }
            )
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
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            ChatRoomScreen(
                viewModel = chatViewModel,
                profileViewModel = profileViewModel,
                chatId = id,
                bubbleColor = (settings ?: AppSettings()).bubbleColor,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
