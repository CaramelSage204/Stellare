package com.example.stellare.ui.verification

import android.content.Context
import android.net.Uri
import com.example.stellare.data.model.VerificationStatus

const val SCAN_ACCESS_LOST_MESSAGE =
    "Dostęp do wybranego zdjęcia wygasł. Wybierz je ponownie."

fun shouldClearResubmitting(status: VerificationStatus?): Boolean =
    status != null && status != VerificationStatus.REJECTED

fun canOpenScan(context: Context, uri: Uri): Boolean =
    try {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (e: Exception) {
        false
    }
