package com.example.stellare.ui.verification

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.data.model.VerificationRequestModel
import com.example.stellare.ui.profile.ProfileViewModel
import com.example.stellare.ui.psychologistdetail.ProfileLoad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.stellare.ui.theme.stellareButtonColors
import com.example.stellare.ui.theme.stellareOutlinedButtonBorder
import android.util.Log
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.CancellationException

@Composable
internal fun VerificationRequestCard(
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
    val scanState by rememberDecodedScan(request.docImageBase64)

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

            when (val scan = scanState) {
                is ScanDecode.Decoded -> Image(
                    bitmap = scan.image.asImageBitmap(),
                    contentDescription = "Skan dokumentu zgłaszającego",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                ScanDecode.Decoding -> ScanPlaceholder { CircularProgressIndicator() }
                ScanDecode.Missing -> ScanPlaceholder { ScanPlaceholderText(ScanDecode.MISSING_MESSAGE) }
                ScanDecode.Unreadable -> ScanPlaceholder { ScanPlaceholderText(ScanDecode.UNREADABLE_MESSAGE) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onApprove,
                    enabled = scanState.allowsApproval(),
                    modifier = Modifier.weight(1f),
                    colors = stellareButtonColors()
                ) {
                    Text("Zatwierdź", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onToggleReject,
                    border = stellareOutlinedButtonBorder(),
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
private fun ScanPlaceholder(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ScanPlaceholderText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
private fun rememberDecodedScan(docImageBase64: String): State<ScanDecode<Bitmap>> =
    produceState<ScanDecode<Bitmap>>(initialValue = ScanDecode.Decoding, key1 = docImageBase64) {
        value = withContext(Dispatchers.IO) {
            ScanDecode.of(docImageBase64, decodeScan(docImageBase64)) { bitmap ->
                ScanSize(widthPx = bitmap.width, heightPx = bitmap.height)
            }
        }
    }

private fun decodeScan(docImageBase64: String): Bitmap? = try {
    val bytes = Base64.decode(docImageBase64, Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.e("Verification", "Nie udalo sie odczytac skanu dokumentu", e)
    null
}

@Composable
private fun rememberSubmitterName(profileViewModel: ProfileViewModel, uid: String): State<String> =
    produceState(initialValue = uid, key1 = uid) {
        val user = (profileViewModel.userById(uid) as? ProfileLoad.Loaded)?.user
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
