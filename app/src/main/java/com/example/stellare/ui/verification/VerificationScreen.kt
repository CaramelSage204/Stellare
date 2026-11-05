package com.example.stellare.ui.verification

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.stellare.data.model.VerificationStatus
import com.example.stellare.ui.components.StaleDataNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.stellare.ui.theme.stellareButtonColors
import androidx.compose.material3.LocalContentColor
import com.example.stellare.ui.theme.stellareOutlinedButtonBorder

enum class VerificationDocType(val storedValue: String, val label: String) {
    DIPLOMA("DIPLOMA", "Dyplom"),
    LICENSE("LICENSE", "Legitymacja"),
    CERTIFICATE("CERTIFICATE", "Certyfikat")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(
    viewModel: VerificationViewModel,
    onBack: () -> Unit
) {
    val myRequest by viewModel.myRequest.collectAsStateWithLifecycle()
    val requestsStale by viewModel.requestsStaleMessage.collectAsStateWithLifecycle()
    val submitError by viewModel.submitError.collectAsStateWithLifecycle()

    var resubmitting by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(myRequest?.status) {
        if (shouldClearResubmitting(myRequest?.status)) {
            resubmitting = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearSubmitError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weryfikacja kwalifikacji", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                val request = myRequest
                when {
                    request == null || resubmitting -> VerificationForm(
                        viewModel = viewModel,
                        submitError = submitError
                    )
                    request.status == VerificationStatus.PENDING -> PendingStatus()
                    request.status == VerificationStatus.APPROVED -> ApprovedStatus()
                    else -> RejectedStatus(
                        reviewNote = request.reviewNote,
                        onResubmit = { resubmitting = true }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerificationForm(
    viewModel: VerificationViewModel,
    submitError: String?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var docType by rememberSaveable { mutableStateOf<VerificationDocType?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var scanAccessLost by remember { mutableStateOf(false) }

    LaunchedEffect(selectedImageUri) {
        val restoredUri = selectedImageUri ?: return@LaunchedEffect
        val readable = withContext(Dispatchers.IO) { canOpenScan(context, restoredUri) }
        if (!readable) {
            selectedImageUri = null
            scanAccessLost = true
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            scanAccessLost = false
            viewModel.clearSubmitError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Prześlij skan dokumentu potwierdzającego Twoje kwalifikacje. " +
                "Administrator sprawdzi zgłoszenie i przyzna odznakę zweryfikowanego konta.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Text(
            text = "Rodzaj dokumentu",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            VerificationDocType.entries.forEach { option ->
                FilterChip(
                    selected = docType == option,
                    onClick = { docType = option },
                    label = { Text(option.label) }
                )
            }
        }

        if (selectedImageUri != null) {
            AsyncImage(
                model = selectedImageUri,
                contentDescription = "Podgląd wybranego skanu",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        OutlinedButton(
            onClick = {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            border = stellareOutlinedButtonBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.UploadFile, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (selectedImageUri == null) "Wybierz zdjęcie dokumentu" else "Zmień zdjęcie")
        }

        if (scanAccessLost) {
            Text(
                text = SCAN_ACCESS_LOST_MESSAGE,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }

        if (submitError != null) {
            Text(
                text = submitError,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }

        Button(
            onClick = {
                val uri = selectedImageUri ?: return@Button
                val selectedDocType = docType ?: return@Button
                coroutineScope.launch {
                    isSubmitting = true
                    viewModel.clearSubmitError()

                    val compressed = withContext(Dispatchers.IO) {
                        compressDocumentScanToBase64(context, uri)
                    }

                    val success = viewModel.submit(compressed, selectedDocType.storedValue)
                    isSubmitting = false
                    if (success) {
                        selectedImageUri = null
                        docType = null
                    }
                }
            },
            enabled = selectedImageUri != null && docType != null && !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = stellareButtonColors()
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Wyślij", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
