package com.example.stellare.ui.verification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.ui.components.StaleDataNotice
import com.example.stellare.ui.profile.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    viewModel: VerificationViewModel,
    profileViewModel: ProfileViewModel,
    isAdmin: Boolean,
    onBack: () -> Unit
) {
    val pendingRequests by viewModel.pendingRequests.collectAsStateWithLifecycle()
    val requestsStale by viewModel.requestsStaleMessage.collectAsStateWithLifecycle()
    val submitError by viewModel.submitError.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var rejectingUid by rememberSaveable { mutableStateOf<String?>(null) }
    var rejectNote by rememberSaveable { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearSubmitError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panel weryfikacji", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrót")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            StaleDataNotice(
                message = requestsStale,
                onRetry = viewModel::retryRequests,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            val decisionError = submitError.takeIf { rejectingUid == null }
            if (decisionError != null) {
                Text(
                    text = decisionError,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when {
                    !isAdmin -> StatusMessage(
                        icon = Icons.Filled.Block,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Brak dostępu",
                        description = "Ta sekcja jest dostępna wyłącznie dla administratorów."
                    )
                    pendingRequests.isEmpty() -> StatusMessage(
                        icon = Icons.Filled.TaskAlt,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Brak oczekujących zgłoszeń",
                        description = "Wszystkie zgłoszenia zostały już rozpatrzone."
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(pendingRequests, key = { it.uid }) { request ->
                            val isRejecting = rejectingUid == request.uid
                            VerificationRequestCard(
                                request = request,
                                profileViewModel = profileViewModel,
                                isRejecting = isRejecting,
                                rejectNote = if (isRejecting) rejectNote else "",
                                rejectError = if (isRejecting) submitError else null,
                                onToggleReject = {
                                    if (isRejecting) {
                                        rejectingUid = null
                                    } else {
                                        viewModel.clearSubmitError()
                                        rejectNote = ""
                                        rejectingUid = request.uid
                                    }
                                },
                                onRejectNoteChange = { rejectNote = it },
                                onApprove = {
                                    coroutineScope.launch {
                                        if (viewModel.decide(request.uid, true, "")) {
                                            rejectingUid = null
                                            rejectNote = ""
                                        }
                                    }
                                },
                                onConfirmReject = {
                                    coroutineScope.launch {
                                        val success = viewModel.decide(request.uid, false, rejectNote)
                                        if (success) {
                                            rejectingUid = null
                                            rejectNote = ""
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
}
