package com.example.stellare.ui.reviews

import com.example.stellare.data.model.RatingModel
import com.example.stellare.ui.PolishPlural
import com.example.stellare.ui.dashboard.ratingBadgeText

const val NO_RATING_PLACEHOLDER = "–"

fun myRatingAverageText(rating: RatingModel): String = ratingBadgeText(rating) ?: NO_RATING_PLACEHOLDER

fun myRatingBasisText(rating: RatingModel): String =
    if (rating.count <= 0) {
        "Brak ocen"
    } else {
        "Na podstawie ${rating.count} ${PolishPlural.form(rating.count, "opinii pacjenta", "opinii pacjentów", "opinii pacjentów")}"
    }
