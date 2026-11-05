package com.example.stellare.ui.dashboard

import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.model.RatingModel
import java.util.Locale

private val PolishLocale = Locale.forLanguageTag("pl-PL")

fun ratingBadgeText(rating: RatingModel): String? =
    if (rating.count <= 0) null else String.format(PolishLocale, "%.1f", rating.average)

fun ratingBadges(ratings: Map<String, RatingModel>): Map<String, String> =
    ratings.mapNotNull { (psychologistId, rating) ->
        ratingBadgeText(rating)?.let { psychologistId to it }
    }.toMap()

fun accentedPostId(posts: List<PostModel>): String? = posts.firstOrNull()?.id

fun priceChipText(post: PostModel): String? =
    if (post.type == PostType.OFFERING_THERAPY && post.priceCoins > 0) "${post.priceCoins} WKT / sesja" else null
