package com.example.stellare.data.repository

import com.example.stellare.data.remote.FirestoreReviewDataSource
import com.example.stellare.data.model.ReviewModel
import kotlinx.coroutines.flow.Flow

class ReviewRepository(private val reviewDataSource: FirestoreReviewDataSource) {

    fun getReviewsForPsychologistFlow(psychologistId: String): Flow<List<ReviewModel>> =
        reviewDataSource.getReviewsForPsychologist(psychologistId)

    suspend fun insertReview(review: ReviewModel) =
        reviewDataSource.saveReview(review)
}