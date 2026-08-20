package com.example.stellare.data.model

data class UserModel(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val age: Int = 0,
    val gender: String = "",
    val phone: String = "",
    val email: String = "",
    val role: UserRole = UserRole.PATIENT,
    val isVerified: Boolean = false,
    val qualifications: String = "",
    val specializations: String = "",
    val pricePerSession: Double = 0.0,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val bio: String = "",
    val isCurrentUser: Boolean = false,
    val customPrices: String = "",
    val profileImageUri: String? = null,
    val coinsBalance: Int = 0
)
