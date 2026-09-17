package com.example.stellare.data.model

import com.google.firebase.firestore.Exclude

enum class VerificationStatus { PENDING, APPROVED, REJECTED }

data class VerificationRequestModel(
    @get:Exclude
    val uid: String = "",
    val docImageBase64: String = "",
    val docType: String = "",
    val status: VerificationStatus = VerificationStatus.PENDING,
    val submittedAt: Long = 0L,
    val reviewedBy: String = "",
    val reviewedAt: Long = 0L,
    val reviewNote: String = ""
)
