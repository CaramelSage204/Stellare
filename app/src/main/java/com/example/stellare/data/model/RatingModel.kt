package com.example.stellare.data.model

import com.google.firebase.firestore.Exclude

data class RatingModel(
    val sum: Int = 0,
    val count: Int = 0
) {
    @get:Exclude
    val average: Double get() = averageRating(sum, count)

    fun plus(rating: Int): RatingModel = RatingModel(sum = sum + rating, count = count + 1)
}
