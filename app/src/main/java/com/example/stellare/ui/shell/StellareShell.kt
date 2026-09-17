package com.example.stellare.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.stellare.di.AppContainer
import com.example.stellare.di.stellareViewModelFactory
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.chat.ChatViewModel
import com.example.stellare.ui.feed.FeedViewModel
import com.example.stellare.ui.navigation.Routes
import com.example.stellare.ui.navigation.StellareNavHost
import com.example.stellare.ui.profile.ProfileViewModel

@Composable
fun StellareShell(container: AppContainer) {
    val navController = rememberNavController()
    val factory = remember(container) { stellareViewModelFactory(container) }

    val feedViewModel: FeedViewModel = viewModel(factory = factory)
    val profileViewModel: ProfileViewModel = viewModel(factory = factory)
    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val chatViewModel: ChatViewModel = viewModel(factory = factory)

    var pokazArkuszProfilu by rememberSaveable { mutableStateOf(false) }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val pokazPasek = currentRoute in Routes.BOTTOM_BAR_ROUTES

    Scaffold(
        bottomBar = {
            if (pokazPasek) {
                BottomBar(currentRoute = currentRoute) { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.FEED) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    ) { paddingValues ->
        StellareNavHost(
            navController = navController,
            container = container,
            onOpenProfileSheet = { pokazArkuszProfilu = true },
            modifier = Modifier.padding(paddingValues)
        )
    }

    if (pokazArkuszProfilu) {
        ProfileSheet(
            feedViewModel = feedViewModel,
            profileViewModel = profileViewModel,
            authViewModel = authViewModel,
            chatViewModel = chatViewModel,
            onNavigate = { route -> navController.navigate(route) },
            onDismiss = { pokazArkuszProfilu = false }
        )
    }
}
