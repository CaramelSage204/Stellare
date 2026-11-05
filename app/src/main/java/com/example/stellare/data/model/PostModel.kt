package com.example.stellare.data.model

import com.google.firebase.firestore.PropertyName

enum class PostType { SEEKING_PSYCHOLOGIST, OFFERING_THERAPY }

data class AuthorSnapshot(
    val firstName: String = "",
    val lastName: String = "",
    val profileImageUri: String? = null,
    val age: Int = 0,
    val gender: String = "",
    @get:PropertyName("isVerified") val isVerified: Boolean = false
)

data class PostModel(
    val id: String = "",
    val authorId: String = "",
    val type: PostType = PostType.SEEKING_PSYCHOLOGIST,
    val title: String = "",
    val body: String = "",
    val tags: List<String> = emptyList(),
    val priceCoins: Int = 0,
    val createdAt: Long = 0L,
    @get:PropertyName("isActive") val isActive: Boolean = true,
    val authorSnapshot: AuthorSnapshot = AuthorSnapshot()
)
