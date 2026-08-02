package com.example.ui.reviews

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ReviewEntity
import com.example.data.repository.ReviewRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val reviewRepository = ReviewRepository(database.reviewDao())
    private val userRepository = UserRepository(database.userDao(), database.favoriteDao())

    val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun getReviewsForPsychologist(psychologistId: Int): Flow<List<ReviewEntity>> = reviewRepository.getReviewsForPsychologistFlow(psychologistId)

    fun addReview(psychologistId: Int, rating: Int, comment: String) {
        viewModelScope.launch {
            val current = currentUser.value
            val reviewerName = if (current != null) "${current.firstName} ${current.lastName}" else "Anonim"
            reviewRepository.insertReview(ReviewEntity(psychologistId = psychologistId, reviewerName = reviewerName, rating = rating, comment = comment))

            // Recalculate psych rating
            val p = userRepository.getUserById(psychologistId) ?: return@launch
            val newCount = p.ratingCount + 1
            val newRating = ((p.rating * p.ratingCount) + rating) / newCount
            userRepository.updateUser(p.copy(rating = newRating, ratingCount = newCount))
        }
    }
}
