package com.example.stellare.data.model

fun averageRating(ratingSum: Int, ratingCount: Int): Double =
    if (ratingCount == 0) 0.0 else ratingSum.toDouble() / ratingCount

const val MAX_STARS = 5

fun filledStars(average: Double): Int {
    if (average.isNaN()) return 0
    val displayed = Math.round(average * 10) / 10.0
    return Math.floor(displayed).toInt().coerceIn(0, MAX_STARS)
}
