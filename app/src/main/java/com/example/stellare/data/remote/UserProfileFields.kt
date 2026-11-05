package com.example.stellare.data.remote

import com.example.stellare.data.model.UserModel
import com.google.firebase.firestore.FieldValue

object UserProfileFields {
    val NAMES = listOf(
        "id", "firstName", "lastName", "age", "gender", "role",
        "isVerified", "isAdmin", "qualifications", "specializations", "pricePerSession",
        "bio", "customPrices", "profileImageUri"
    )

    val LEGACY_PRIVATE_NAMES = listOf("phone", "email", "coinsBalance")

    fun legacyPrivateDeletions(): Map<String, Any> = LEGACY_PRIVATE_NAMES.associateWith { FieldValue.delete() }

    fun profileFields(user: UserModel): Map<String, Any?> = mapOf(
        "id" to user.id,
        "firstName" to user.firstName,
        "lastName" to user.lastName,
        "age" to user.age,
        "gender" to user.gender,
        "role" to user.role.name,
        "isVerified" to user.isVerified,
        "isAdmin" to user.isAdmin,
        "qualifications" to user.qualifications,
        "specializations" to user.specializations,
        "pricePerSession" to user.pricePerSession,
        "bio" to user.bio,
        "customPrices" to user.customPrices,
        "profileImageUri" to user.profileImageUri
    )

    fun profileWrite(user: UserModel): Map<String, Any?> = profileFields(user) + legacyPrivateDeletions()

    fun verificationApproval(): Map<String, Any> = mapOf("isVerified" to true) + legacyPrivateDeletions()
}
