package com.example.stellare.ui.auth

import android.util.Log
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.ui.UserFacingError
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class PasswordResetController(
    private val scope: CoroutineScope,
    clock: () -> Long,
    private val authDataSource: AuthDataSource
) {

    private val _state = MutableStateFlow<PasswordResetState>(PasswordResetState.Idle)
    val state: StateFlow<PasswordResetState> = _state.asStateFlow()

    private val cooldown = CooldownTimer(scope, clock, COOLDOWN_MS)
    val cooldownSeconds: StateFlow<Int> = cooldown.seconds

    fun send(email: String) {
        if (_state.value == PasswordResetState.Sending) return
        if (cooldown.isRunning()) return
        val trimmed = email.trim()
        if (trimmed.isEmpty()) {
            _state.value = PasswordResetState.Failed(PasswordResetMessages.EMPTY_EMAIL)
            return
        }
        scope.launch {
            _state.value = PasswordResetState.Sending
            _state.value = try {
                authDataSource.sendPasswordResetEmail(trimmed)
                cooldown.start()
                PasswordResetState.Sent(PasswordResetMessages.SENT)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Auth", "Password reset failed", e)
                if (isUnknownAccount(e)) {
                    cooldown.start()
                    PasswordResetState.Sent(PasswordResetMessages.SENT)
                } else PasswordResetState.Failed(UserFacingError.authMessage(e, PasswordResetMessages.FAILED))
            }
        }
    }

    fun reset() {
        if (_state.value == PasswordResetState.Sending) return
        _state.value = PasswordResetState.Idle
    }

    private fun isUnknownAccount(error: Throwable): Boolean =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseAuthException>()
            .any { it.errorCode == "ERROR_USER_NOT_FOUND" }

    private companion object {
        const val COOLDOWN_MS = 60_000L
    }
}
