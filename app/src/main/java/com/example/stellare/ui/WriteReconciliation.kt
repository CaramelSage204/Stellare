package com.example.stellare.ui

import com.example.stellare.data.model.UserMessageException
import kotlinx.coroutines.CancellationException

sealed interface WriteOutcome<out T> {
    data class Succeeded<T>(val value: T) : WriteOutcome<T>
    data class Failed(val message: String) : WriteOutcome<Nothing>
    data class Unknown(val message: String) : WriteOutcome<Nothing>
}

object WriteReconciliation {

    suspend fun <T : Any> attempt(
        write: suspend () -> T,
        landed: suspend () -> T?,
        failureMessage: String,
        uncertainMessage: String,
        verifyFirst: Boolean = false,
        onError: (Exception) -> Unit = {}
    ): WriteOutcome<T> {
        if (verifyFirst) {
            when (val probe = probe(landed)) {
                is Probe.Found -> return WriteOutcome.Succeeded(probe.value)
                is Probe.Broken -> return WriteOutcome.Unknown(uncertainMessage)
                Probe.Absent -> Unit
            }
        }
        return try {
            WriteOutcome.Succeeded(write())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
            outcome(e, failureMessage, uncertainMessage, landed)
        }
    }

    suspend fun <T : Any> outcome(
        error: Exception,
        failureMessage: String,
        uncertainMessage: String,
        landed: suspend () -> T?
    ): WriteOutcome<T> {
        if (error is CancellationException) throw error
        if (leavesNoState(error)) return WriteOutcome.Failed(UserFacingError.message(error, failureMessage))
        return when (val probe = probe(landed)) {
            is Probe.Found -> WriteOutcome.Succeeded(probe.value)
            Probe.Absent -> WriteOutcome.Failed(UserFacingError.message(error, failureMessage))
            is Probe.Broken -> WriteOutcome.Unknown(uncertainMessage)
        }
    }

    private fun leavesNoState(error: Throwable): Boolean =
        isPermanentFailure(error) || generateSequence(error) { it.cause }.any { it is UserMessageException }

    private suspend fun <T : Any> probe(landed: suspend () -> T?): Probe<T> = try {
        landed()?.let { Probe.Found(it) } ?: Probe.Absent
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Probe.Broken
    }

    private sealed interface Probe<out T> {
        data class Found<T>(val value: T) : Probe<T>
        data object Absent : Probe<Nothing>
        data object Broken : Probe<Nothing>
    }
}

object UncertainWriteMessages {
    const val BOOKING = "Nie wiemy, czy rezerwacja została zapisana. Sprawdź swoje wizyty."
    const val COMPLETION = "Nie wiemy, czy wizyta została zakończona. Sprawdź stan wizyty."
    const val CANCELLATION = "Nie wiemy, czy wizyta została odwołana. Sprawdź stan wizyty."
    const val RECLAIM = "Nie wiemy, czy monety zostały zwrócone. Sprawdź stan wizyty i saldo."
    const val TOP_UP = "Nie wiemy, czy doładowanie zostało zapisane. Sprawdź saldo i historię portfela."
    const val PROFILE_SAVE = "Nie wiemy, czy profil został zapisany. Sprawdź swój profil."
    const val PROFILE_POSTS_SYNC = "Profil został zapisany. Nie wiemy, czy Twoje ogłoszenia dostały nowe dane. Sprawdź je."
}
