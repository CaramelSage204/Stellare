package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.local.UserRole

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firstName: String,
    val lastName: String,
    val age: Int,
    val gender: String,
    val phone: String,
    val email: String,
    val role: UserRole,
    val isVerified: Boolean = false,
    val qualifications: String = "", // e.g. "Licencjat", "Doktorant", "Certyfikat"
    val specializations: String = "", // e.g. "Rodzina, Ogólny, Praca"
    val pricePerSession: Double = 0.0,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val bio: String = "",
    val isCurrentUser: Boolean = false,
    val customPrices: String = "",
    val profileImageUri: String? = null,
    val coinsBalance: Int = 100
)
