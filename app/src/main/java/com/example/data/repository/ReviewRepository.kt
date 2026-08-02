package com.example.data.repository

import com.example.data.local.dao.ReviewDao
import com.example.data.local.entity.ReviewEntity
import kotlinx.coroutines.flow.Flow

class ReviewRepository(private val reviewDao: ReviewDao) {
    fun getReviewsForPsychologistFlow(psychologistId: Int): Flow<List<ReviewEntity>> = reviewDao.getReviewsForPsychologistFlow(psychologistId)
    suspend fun insertReview(review: ReviewEntity): Long = reviewDao.insertReview(review)
}
