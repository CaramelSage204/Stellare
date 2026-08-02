package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.ReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE psychologistId = :psychologistId ORDER BY timestamp DESC")
    fun getReviewsForPsychologistFlow(psychologistId: Int): Flow<List<ReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: ReviewEntity): Long
}
