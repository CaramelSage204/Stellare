package com.example.stellare.ui.auth

import android.util.Log
import com.example.stellare.data.model.UserMessageException
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.remote.PhoneCodeRequest
import com.example.stellare.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class PhoneVerificationController(
    private val scope: CoroutineScope,
    clock: () -> Long,
    private val authDataSource: AuthDataSource,
    private val userRepository: UserRepository
) {

    private val _state = MutableStateFlow<PhoneVerificationState>(PhoneVerificationState.EnteringNumber())
    val state: StateFlow<PhoneVerificationState> = _state.asStateFlow()

    private val resendCooldown = CooldownTimer(scope, clock, RESEND_COOLDOWN_MS)
    val resendCooldownSeconds: StateFlow<Int> = resendCooldown.seconds

    private val _offer = MutableStateFlow<String?>(null)
    val offer: StateFlow<String?> = _offer.asStateFlow()

    private var pendingVerificationId: String? = null
    private var job: Job? = null

    fun open() {
        if (_state.value !is PhoneVerificationState.EnteringNumber &&
            _state.value !is PhoneVerificationState.Verified
        ) return
        val linked = authDataSource.getCurrentUserPhoneNumberOnce()
        if (linked == null) {
            _state.value = PhoneVerificationState.EnteringNumber()
            return
        }
        _state.value = PhoneVerificationState.Verified(linked)
        saveLinkedPhoneToProfile(linked)
    }

    fun requestCode(input: String) {
        val current = _state.value
        if (current !is PhoneVerificationState.EnteringNumber && current !is PhoneVerificationState.EnteringCode) return
        if (resendCooldown.isRunning()) return
        val number = PhoneNumber.normalize(input)
        if (number == null) {
            _state.value = codeEntryFailure(current, PhoneVerificationMessages.INVALID_NUMBER)
            return
        }
        _state.value = PhoneVerificationState.SendingCode
        job = scope.launch {
            try {
                when (val request = authDataSource.requestPhoneCode(number)) {
                    is PhoneCodeRequest.CodeSent -> {
                        pendingVerificationId = request.verificationId
                        resendCooldown.start()
                        _state.value = PhoneVerificationState.EnteringCode(number)
                    }
                    is PhoneCodeRequest.AlreadyLinked -> completePhoneLinking(request.phoneNumber)
                    null -> _state.value = codeEntryFailure(current, PhoneVerificationMessages.SEND_FAILED)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Auth", "Phone code request failed", e)
                val failure = PhoneVerificationFailure.of(e, PhoneVerificationMessages.SEND_FAILED)
                _state.value = codeEntryFailure(current, failure.message)
            }
        }
    }

    fun confirmCode(code: String) {
        val current = _state.value as? PhoneVerificationState.EnteringCode ?: return
        val verificationId = pendingVerificationId
        if (verificationId == null) {
            _state.value = PhoneVerificationState.EnteringNumber(PhoneVerificationMessages.CODE_EXPIRED)
            return
        }
        val trimmed = code.trim()
        if (trimmed.length != CODE_LENGTH || !trimmed.all { it in '0'..'9' }) {
            _state.value = current.copy(error = PhoneVerificationMessages.INVALID_CODE_FORMAT)
            return
        }
        _state.value = PhoneVerificationState.CheckingCode(current.phoneNumber)
        job = scope.launch {
            try {
                val linked = authDataSource.confirmPhoneCode(verificationId, trimmed)
                if (linked == null) {
                    _state.value = current.copy(error = PhoneVerificationMessages.CONFIRM_FAILED)
                } else {
                    completePhoneLinking(linked)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Auth", "Phone code confirmation failed", e)
                val failure = PhoneVerificationFailure.of(e, PhoneVerificationMessages.CONFIRM_FAILED)
                if (failure.needsNewNumber) {
                    pendingVerificationId = null
                    _state.value = PhoneVerificationState.EnteringNumber(failure.message)
                } else {
                    _state.value = current.copy(error = failure.message)
                }
            }
        }
    }

    fun changeNumber() {
        if (_state.value !is PhoneVerificationState.EnteringCode) return
        pendingVerificationId = null
        _state.value = PhoneVerificationState.EnteringNumber()
    }

    fun retrySavingToProfile() {
        val current = _state.value as? PhoneVerificationState.Verified ?: return
        if (!current.profileSaveFailed) return
        saveLinkedPhoneToProfile(current.phoneNumber)
    }

    fun close() {
        val current = _state.value
        if (current is PhoneVerificationState.SendingCode || current is PhoneVerificationState.CheckingCode) return
        reset()
    }

    fun takeOffer(): String? =
        _offer.value.also { _offer.value = null }

    fun dismissOffer() {
        _offer.value = null
    }

    fun reset() {
        job?.cancel()
        pendingVerificationId = null
        _state.value = PhoneVerificationState.EnteringNumber()
    }

    private fun codeEntryFailure(previous: PhoneVerificationState, message: String): PhoneVerificationState =
        if (previous is PhoneVerificationState.EnteringCode) previous.copy(error = message)
        else PhoneVerificationState.EnteringNumber(message)

    private fun completePhoneLinking(linked: String) {
        pendingVerificationId = null
        _state.value = PhoneVerificationState.Verified(linked)
        saveLinkedPhoneToProfile(linked)
    }

    private fun saveLinkedPhoneToProfile(phone: String) {
        scope.launch {
            val failed = try {
                val uid = authDataSource.getCurrentUserIdOnce()
                    ?: throw UserMessageException(PhoneVerificationMessages.SESSION_LOST)
                if (userRepository.currentPrivateProfile()?.phone != phone) userRepository.savePhone(uid, phone)
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Auth", "Saving verified phone to profile failed", e)
                true
            }
            _state.update { current ->
                if (current is PhoneVerificationState.Verified && current.phoneNumber == phone) {
                    current.copy(profileSaveFailed = failed)
                } else current
            }
        }
    }

    fun offerVerification(number: String) {
        _offer.value = number
    }

    private companion object {
        const val RESEND_COOLDOWN_MS = 60_000L
        const val CODE_LENGTH = 6
    }
}
