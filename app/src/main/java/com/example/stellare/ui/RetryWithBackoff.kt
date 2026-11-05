package com.example.stellare.ui

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen

private const val BASE_RETRY_DELAY_MS = 1_000L
private const val MAX_RETRY_DELAY_MS = 30_000L
private const val MAX_BACKOFF_SHIFT = 5

private val PERMANENT_CODES = setOf(
    FirebaseFirestoreException.Code.PERMISSION_DENIED,
    FirebaseFirestoreException.Code.INVALID_ARGUMENT,
    FirebaseFirestoreException.Code.UNIMPLEMENTED
)

internal fun isPermanentFailure(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().any { it.code in PERMANENT_CODES }

fun <T> Flow<T>.retryWithBackoff(
    tag: String,
    what: String,
    onFailure: suspend FlowCollector<T>.() -> Unit = {}
): Flow<T> = retryWhen { cause, attempt ->
    if (cause is CancellationException || isPermanentFailure(cause)) {
        false
    } else {
        Log.e(tag, "Nie udalo sie wczytac $what, ponawiam", cause)
        onFailure()
        delay(minOf(MAX_RETRY_DELAY_MS, BASE_RETRY_DELAY_MS shl attempt.toInt().coerceAtMost(MAX_BACKOFF_SHIFT)))
        true
    }
}.catch { cause ->
    if (cause is CancellationException || !isPermanentFailure(cause)) throw cause
    Log.e(tag, "Odmowa trwala przy wczytywaniu $what, nie ponawiam", cause)
    onFailure()
}
