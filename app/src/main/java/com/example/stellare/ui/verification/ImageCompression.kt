package com.example.stellare.ui.verification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

const val MAX_DECODE_PIXELS = 4_000_000
const val TARGET_SCAN_BYTES = 500 * 1024
const val FIRESTORE_DOCUMENT_SAFE_LIMIT_BYTES = 900 * 1024
const val MIN_JPEG_QUALITY = 40
const val MAX_JPEG_QUALITY = 90
private const val MAX_COMPRESSION_ATTEMPTS = 3

const val COMPRESSION_TOO_LARGE_MESSAGE =
    "Zdjęcie jest zbyt duże. Spróbuj zrobić je ponownie w gorszym oświetleniu lub wybierz inne."

const val COMPRESSION_UNREADABLE_MESSAGE =
    "Nie udało się odczytać zdjęcia. Wybierz inny plik lub zrób nowe zdjęcie."

sealed interface DocumentScanCompressionResult {
    data class Success(val base64: String) : DocumentScanCompressionResult
    data object UnreadableImage : DocumentScanCompressionResult
    data object TooLarge : DocumentScanCompressionResult
}

fun sampleSizeFor(width: Int, height: Int, maxPixels: Int = MAX_DECODE_PIXELS): Int {
    require(width > 0) { "width musi być dodatnie" }
    require(height > 0) { "height musi być dodatnie" }
    require(maxPixels > 0) { "maxPixels musi być dodatnie" }
    var sampleSize = 1
    while ((width / sampleSize).toLong() * (height / sampleSize).toLong() > maxPixels) {
        sampleSize *= 2
    }
    return sampleSize
}

fun qualityFor(sizeBytes: Int, targetBytes: Int = TARGET_SCAN_BYTES): Int {
    require(sizeBytes > 0) { "sizeBytes musi być dodatnie" }
    require(targetBytes > 0) { "targetBytes musi być dodatnie" }
    val scaled = (MAX_JPEG_QUALITY.toDouble() * targetBytes / sizeBytes).toInt()
    return scaled.coerceIn(MIN_JPEG_QUALITY, MAX_JPEG_QUALITY)
}

fun fitsInDocument(base64Length: Int, limitBytes: Int = FIRESTORE_DOCUMENT_SAFE_LIMIT_BYTES): Boolean {
    require(base64Length >= 0) { "base64Length nie może być ujemne" }
    return base64Length <= limitBytes
}

fun compressDocumentScanToBase64(context: Context, uri: Uri): DocumentScanCompressionResult {
    val bounds = readImageBounds(context, uri)
        ?: return DocumentScanCompressionResult.UnreadableImage
    val sampleSize = sampleSizeFor(bounds.first, bounds.second, MAX_DECODE_PIXELS)
    val bitmap = decodeSampledBitmap(context, uri, sampleSize)
        ?: return DocumentScanCompressionResult.UnreadableImage
    return try {
        val jpegBytes = compressToTargetSize(bitmap)
        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        if (fitsInDocument(base64.length)) {
            DocumentScanCompressionResult.Success(base64)
        } else {
            DocumentScanCompressionResult.TooLarge
        }
    } finally {
        bitmap.recycle()
    }
}

private fun readImageBounds(context: Context, uri: Uri): Pair<Int, Int>? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val stream = context.contentResolver.openInputStream(uri) ?: return null
    stream.use { input -> BitmapFactory.decodeStream(input, null, options) }
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    return options.outWidth to options.outHeight
}

private fun decodeSampledBitmap(context: Context, uri: Uri, sampleSize: Int): Bitmap? {
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    }
}

private fun compressToTargetSize(bitmap: Bitmap): ByteArray {
    var quality = MAX_JPEG_QUALITY
    var bytes = encodeJpeg(bitmap, quality)
    var attempt = 0
    while (bytes.size > TARGET_SCAN_BYTES && attempt < MAX_COMPRESSION_ATTEMPTS) {
        quality = qualityFor(bytes.size, TARGET_SCAN_BYTES)
        bytes = encodeJpeg(bitmap, quality)
        attempt++
    }
    return bytes
}

private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
    return output.toByteArray()
}
