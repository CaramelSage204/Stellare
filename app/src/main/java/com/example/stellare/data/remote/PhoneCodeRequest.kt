package com.example.stellare.data.remote

sealed interface PhoneCodeRequest {
    data class CodeSent(val verificationId: String) : PhoneCodeRequest
    data class AlreadyLinked(val phoneNumber: String) : PhoneCodeRequest
}
