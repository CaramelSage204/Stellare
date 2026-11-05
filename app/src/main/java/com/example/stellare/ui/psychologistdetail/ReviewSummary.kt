package com.example.stellare.ui.psychologistdetail

import com.example.stellare.data.model.ReviewModel
import com.example.stellare.data.model.averageRating

private val StarLevels = 5 downTo 1

data class ReviewSummary(
    val count: Int,
    val average: Double,
    val countsByStars: Map<Int, Int>,
    val isLoading: Boolean = false,
    val failureMessage: String? = null
) {
    val isFailed: Boolean get() = failureMessage != null

    val hasReviews: Boolean get() = !isLoading && !isFailed && count > 0

    val isEmpty: Boolean get() = !isLoading && !isFailed && count == 0

    val showHistogram: Boolean get() = hasReviews

    fun shareOf(stars: Int): Float =
        if (count == 0) 0f else (countsByStars[stars] ?: 0).toFloat() / count

    companion object {
        fun loading() = ReviewSummary(
            count = 0,
            average = 0.0,
            countsByStars = StarLevels.associateWith { 0 },
            isLoading = true
        )

        fun failed(message: String) = ReviewSummary(
            count = 0,
            average = 0.0,
            countsByStars = StarLevels.associateWith { 0 },
            failureMessage = message
        )

        fun fromLoad(load: ReviewsLoad?): ReviewSummary = when (load) {
            null -> loading()
            is ReviewsLoad.Loaded -> from(load.reviews)
            is ReviewsLoad.Failed -> failed(load.message)
        }

        fun from(reviews: List<ReviewModel>?): ReviewSummary {
            if (reviews == null) return loading()
            val valid = reviews.filter { it.rating in 1..5 }
            return ReviewSummary(
                count = valid.size,
                average = averageRating(valid.sumOf { it.rating }, valid.size),
                countsByStars = StarLevels.associateWith { stars -> valid.count { it.rating == stars } }
            )
        }
    }
}
