package com.example.stellare.data.repository

import com.example.stellare.data.remote.ReviewDataSource
import com.example.stellare.data.model.ReviewModel
import kotlinx.coroutines.flow.Flow

class ReviewRepository(private val reviewDataSource: ReviewDataSource) {

    fun getReviewsForPsychologistFlow(psychologistId: String): Flow<List<ReviewModel>> =
        reviewDataSource.getReviewsForPsychologist(psychologistId)

    suspend fun insertReview(review: ReviewModel) =
        reviewDataSource.saveReview(review)

    suspend fun saveReviewWithRating(
        review: ReviewModel,
        psychologistId: String,
        newRating: Double,
        newRatingCount: Int
    ) = reviewDataSource.saveReviewWithRating(review, psychologistId, newRating, newRatingCount)
}