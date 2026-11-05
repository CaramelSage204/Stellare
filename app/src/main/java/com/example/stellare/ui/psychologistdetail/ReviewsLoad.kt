package com.example.stellare.ui.psychologistdetail

import com.example.stellare.data.model.ReviewModel

sealed interface ReviewsLoad {
    data class Loaded(val reviews: List<ReviewModel>) : ReviewsLoad
    data class Failed(val message: String) : ReviewsLoad
}
