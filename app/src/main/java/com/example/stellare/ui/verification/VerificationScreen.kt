package com.example.stellare.ui.verification

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.stellare.data.model.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val submitError by viewModel.submitError.collectAsStateWithLifecycle()

    var resubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(myRequest?.status) {
        if (myRequest?.status != VerificationStatus.REJECTED) {
            resubmitting = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearSubmitError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weryfikacja kwalifikacji") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrót")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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

@Composable
private fun VerificationForm(
    viewModel: VerificationViewModel,
    submitError: String?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var docType by remember { mutableStateOf<VerificationDocType?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.UploadFile, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (selectedImageUri == null) "Wybierz zdjęcie dokumentu" else "Zmień zdjęcie")
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
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Wyślij", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun StatusMessage(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    content: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        content()
    }
}

@Composable
private fun PendingStatus() {
    StatusMessage(
        icon = Icons.Filled.HourglassTop,
        iconTint = MaterialTheme.colorScheme.primary,
        title = "Zgłoszenie oczekuje na rozpatrzenie",
        description = "Administrator sprawdzi przesłany dokument najszybciej, jak to możliwe."
    )
}

@Composable
private fun ApprovedStatus() {
    StatusMessage(
        icon = Icons.Filled.CheckCircle,
        iconTint = MaterialTheme.colorScheme.primary,
        title = "Kwalifikacje zweryfikowane",
        description = "Twoje konto ma teraz odznakę zweryfikowanego użytkownika."
    )
}

@Composable
private fun RejectedStatus(reviewNote: String, onResubmit: () -> Unit) {
    StatusMessage(
        icon = Icons.Filled.Cancel,
        iconTint = MaterialTheme.colorScheme.error,
        title = "Zgłoszenie zostało odrzucone",
        description = if (reviewNote.isNotBlank()) {
            "Powód: $reviewNote"
        } else {
            "Administrator nie podał powodu odrzucenia."
        }
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onResubmit,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Wyślij ponownie", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}
