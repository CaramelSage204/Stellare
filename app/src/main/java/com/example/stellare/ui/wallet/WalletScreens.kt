package com.example.stellare.ui.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.ui.components.StaleDataNotice
import com.example.stellare.ui.profile.ProfileViewModel

@Composable
fun WalletScreen(
    viewModel: WalletViewModel,
    profileViewModel: ProfileViewModel,
    onOpenProfileSheet: () -> Unit
) {
    val currentUser by profileViewModel.currentUser.collectAsStateWithLifecycle()
    val profileReadFailure by profileViewModel.profileReadFailure.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val historyStale by viewModel.historyStaleMessage.collectAsStateWithLifecycle()
    val topUpError by viewModel.topUpError.collectAsStateWithLifecycle()
    val topUpInProgress by viewModel.topUpInProgress.collectAsStateWithLifecycle()
    val topUpCompleted by viewModel.topUpCompleted.collectAsStateWithLifecycle()

    var showPacksDialog by remember { mutableStateOf(false) }

    var showCheckoutDialog by remember { mutableStateOf<CoinPack?>(null) }
    var showSuccessOverlay by remember { mutableStateOf(false) }
    var lastAddedCoins by remember { mutableStateOf(0) }

    var showDetailsMenu by remember { mutableStateOf(false) }

    LaunchedEffect(topUpCompleted) {
        topUpCompleted?.let {
            lastAddedCoins = it
            showCheckoutDialog = null
            showSuccessOverlay = true
            viewModel.consumeTopUpCompleted()
        }
    }

    val bgMain = MaterialTheme.colorScheme.background
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgMain
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            WalletTopBar(
                currentUser = currentUser,
                onOpenProfileSheet = onOpenProfileSheet,
                onToggleDetails = { showDetailsMenu = !showDetailsMenu }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {

                StaleDataNotice(message = profileReadFailure, onRetry = profileViewModel::retryProfile)

                WalletBalance(balance)

                Spacer(modifier = Modifier.height(20.dp))

                WalletTopUpAction(onTopUp = { showPacksDialog = true })

                Text(
                    text = "Wypłata monet na konto ani przelewy między użytkownikami nie są dostępne w tej wersji.",
                    color = textSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                StaleDataNotice(message = historyStale, onRetry = viewModel::retryHistory)
                WalletHistorySection(history = history, onRetryNames = viewModel::retryNames)
            }
        }

        if (showDetailsMenu) {
            WalletDetailsDialog(onDismiss = { showDetailsMenu = false })
        }

        val checkoutPack = showCheckoutDialog
        if (checkoutPack != null) {
            TopUpCheckoutDialog(
                pack = checkoutPack,
                topUpError = topUpError,
                topUpInProgress = topUpInProgress,
                onConfirm = { viewModel.topUp(checkoutPack.coins) },
                onCancel = {
                    viewModel.clearTopUpError()
                    showCheckoutDialog = null
                },
                onDismissRequest = { showCheckoutDialog = null }
            )
        }

        if (showSuccessOverlay) {
            TopUpSuccessDialog(addedCoins = lastAddedCoins, onDismiss = { showSuccessOverlay = false })
        }

        if (showPacksDialog) {
            CoinPacksDialog(
                packs = CoinPacks,
                onPackSelected = { pack ->
                    viewModel.clearTopUpError()
                    showCheckoutDialog = pack
                    showPacksDialog = false
                },
                onDismiss = { showPacksDialog = false }
            )
        }
    }
}
