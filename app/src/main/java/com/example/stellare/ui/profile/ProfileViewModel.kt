package com.example.stellare.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.ReviewModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.ReviewRepository
import com.example.stellare.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val reviewRepository: ReviewRepository
) : ViewModel() {

    val currentUser: StateFlow<UserModel?> = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun userById(userId: String): UserModel? = userRepository.getUserById(userId)

    fun reviewsFor(psychologistId: String): Flow<List<ReviewModel>> =
        reviewRepository.getReviewsForPsychologistFlow(psychologistId)

    fun updateProfile(
        firstName: String,
        lastName: String,
        age: Int,
        gender: String,
        bio: String,
        specializations: String,
        pricePerSession: Double,
        customPrices: String = "",
        profileImageUri: String? = null,
        qualifications: String? = null
    ) {
        viewModelScope.launch {
            try {
                val current = userRepository.getCurrentUser() ?: return@launch
                val updated = current.copy(
                    firstName = firstName,
                    lastName = lastName,
                    age = age,
                    gender = gender,
                    bio = bio,
                    specializations = specializations,
                    pricePerSession = pricePerSession,
                    customPrices = customPrices,
                    profileImageUri = profileImageUri ?: current.profileImageUri,
                    qualifications = qualifications ?: current.qualifications
                )
                userRepository.updateUser(updated)
            } catch (e: Exception) {
                Log.e("Profile", "Nie udalo sie zapisac profilu", e)
            }
        }
    }

    fun addReview(psychologistId: String, rating: Int, comment: String) {
        viewModelScope.launch {
            try {
                val current = userRepository.getCurrentUser() ?: return@launch
                val psychologist = userRepository.getUserById(psychologistId) ?: return@launch
                val reviewId = "${psychologistId}_${current.id}"
                val newCount = psychologist.ratingCount + 1
                val newRating = ((psychologist.rating * psychologist.ratingCount) + rating) / newCount

                val review = ReviewModel(
                    reviewId = reviewId,
                    psychologistId = psychologistId,
                    reviewerId = current.id,
                    reviewerName = "${current.firstName} ${current.lastName}",
                    rating = rating,
                    comment = comment,
                    timestamp = System.currentTimeMillis()
                )
                reviewRepository.saveReviewWithRating(review, psychologistId, newRating, newCount)
            } catch (e: Exception) {
                Log.e("Reviews", "Nie udalo sie dodac recenzji", e)
            }
        }
    }
}
