package com.example.stellare.data.model

data class ReviewModel(
    val reviewId: String = "",
    val psychologistId: String = "",
    val reviewerName: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val timestamp: Long = 0L
)
