package com.example.stellare.ui.verification

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.data.model.VerificationRequestModel
import com.example.stellare.ui.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    viewModel: VerificationViewModel,
    profileViewModel: ProfileViewModel,
    isAdmin: Boolean,
    onBack: () -> Unit
) {
    val pendingRequests by viewModel.pendingRequests.collectAsStateWithLifecycle()
    val submitError by viewModel.submitError.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var rejectingUid by remember { mutableStateOf<String?>(null) }
    var rejectNote by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearSubmitError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panel weryfikacji") },
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
            when {
                !isAdmin -> CenteredMessage(
                    icon = Icons.Filled.Block,
                    title = "Brak dostępu",
                    description = "Ta sekcja jest dostępna wyłącznie dla administratorów."
                )
                pendingRequests.isEmpty() -> CenteredMessage(
                    icon = Icons.Filled.TaskAlt,
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
                                coroutineScope.launch { viewModel.decide(request.uid, true, "") }
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

@Composable
private fun VerificationRequestCard(
    request: VerificationRequestModel,
    profileViewModel: ProfileViewModel,
    isRejecting: Boolean,
    rejectNote: String,
    rejectError: String?,
    onToggleReject: () -> Unit,
    onRejectNoteChange: (String) -> Unit,
    onApprove: () -> Unit,
    onConfirmReject: () -> Unit
) {
    val submitterName by rememberSubmitterName(profileViewModel, request.uid)
    val scanBitmap by rememberDecodedScan(request.docImageBase64)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = submitterName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                text = formatSubmittedAt(request.submittedAt),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
            Text(
                text = docTypeLabel(request.docType),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            val bitmap = scanBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Skan dokumentu zgłaszającego",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Zatwierdź", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onToggleReject,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isRejecting) "Anuluj" else "Odrzuć")
                }
            }

            if (isRejecting) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = rejectNote,
                    onValueChange = onRejectNoteChange,
                    label = { Text("Powód odrzucenia") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (rejectError != null) {
                    Text(
                        text = rejectError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onConfirmReject,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Potwierdź odrzucenie")
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(icon: ImageVector, title: String, description: String) {
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
            tint = MaterialTheme.colorScheme.primary,
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
    }
}

@Composable
private fun rememberDecodedScan(docImageBase64: String): State<Bitmap?> =
    produceState<Bitmap?>(initialValue = null, key1 = docImageBase64) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Base64.decode(docImageBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }

@Composable
private fun rememberSubmitterName(profileViewModel: ProfileViewModel, uid: String): State<String> =
    produceState(initialValue = uid, key1 = uid) {
        val user = runCatching { profileViewModel.userById(uid) }.getOrNull()
        val fullName = "${user?.firstName.orEmpty()} ${user?.lastName.orEmpty()}".trim()
        value = fullName.ifEmpty { uid }
    }

private fun formatSubmittedAt(submittedAt: Long): String {
    if (submittedAt <= 0L) return "Data nieznana"
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("pl-PL"))
    return formatter.format(Date(submittedAt))
}

private fun docTypeLabel(storedValue: String): String =
    VerificationDocType.entries.firstOrNull { it.storedValue == storedValue }?.label ?: storedValue
