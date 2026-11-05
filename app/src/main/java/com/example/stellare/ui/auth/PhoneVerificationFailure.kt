package com.example.stellare.ui.auth

import com.example.stellare.ui.UserFacingError
import com.google.firebase.auth.FirebaseAuthException

data class PhoneVerificationFailure(val message: String, val needsNewNumber: Boolean = false) {

    companion object {
        fun of(error: Throwable, fallback: String): PhoneVerificationFailure {
            val code = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseAuthException>()
                .firstOrNull()
                ?.errorCode
            return when (code) {
                "ERROR_INVALID_VERIFICATION_CODE" -> PhoneVerificationFailure(PhoneVerificationMessages.WRONG_CODE)
                "ERROR_SESSION_EXPIRED" -> PhoneVerificationFailure(PhoneVerificationMessages.CODE_EXPIRED)
                "ERROR_INVALID_PHONE_NUMBER" -> PhoneVerificationFailure(PhoneVerificationMessages.INVALID_NUMBER, needsNewNumber = true)
                "ERROR_QUOTA_EXCEEDED" -> PhoneVerificationFailure(PhoneVerificationMessages.QUOTA_EXCEEDED)
                "ERROR_CREDENTIAL_ALREADY_IN_USE" -> PhoneVerificationFailure(PhoneVerificationMessages.NUMBER_IN_USE, needsNewNumber = true)
                "ERROR_PROVIDER_ALREADY_LINKED" -> PhoneVerificationFailure(PhoneVerificationMessages.ALREADY_LINKED, needsNewNumber = true)
                else -> PhoneVerificationFailure(UserFacingError.message(error, fallback))
            }
        }
    }
}
