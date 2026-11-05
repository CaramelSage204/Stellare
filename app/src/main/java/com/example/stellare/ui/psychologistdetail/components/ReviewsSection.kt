package com.example.stellare.ui.psychologistdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.data.model.ReviewModel
import com.example.stellare.ui.PolishPlural
import com.example.stellare.ui.RelativeTime
import com.example.stellare.ui.profile.ReviewSubmitState
import com.example.stellare.ui.psychologistdetail.ReviewComposer
import com.example.stellare.ui.psychologistdetail.ReviewFooter
import com.example.stellare.ui.psychologistdetail.ReviewSummary
import com.example.stellare.ui.psychologistdetail.ratingCaption
import com.example.stellare.ui.psychologistdetail.reviewFooter
import com.example.stellare.ui.components.RatingStars

private val StarLevels = listOf(5, 4, 3, 2, 1)

@Composable
fun ReviewsSection(
    summary: ReviewSummary,
    reviews: List<ReviewModel>,
    currentUserId: String?,
    hasCompletedSession: Boolean,
    reviewState: ReviewSubmitState,
    nowMillis: Long,
    onSubmitReview: (rating: Int, comment: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle("Opinie pacjentów i oceny", Modifier.padding(bottom = 12.dp))
        RatingOverview(summary = summary, onRetry = onRetry)
        Spacer(modifier = Modifier.height(16.dp))

        val ownReview = reviews.firstOrNull { it.reviewerId == currentUserId }
        when (reviewFooter(hasOwnReview = ownReview != null, hasCompletedSession = hasCompletedSession)) {
            ReviewFooter.OwnReview -> Text(
                "Twoja opinia: ${ownReview?.rating} z 5",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            ReviewFooter.Composer -> ReviewComposer(
                state = reviewState,
                onSubmit = onSubmitReview,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            ReviewFooter.Nothing -> Unit
        }

        reviews.forEach { review -> ReviewCard(review = review, nowMillis = nowMillis) }
    }
}

@Composable
private fun RatingOverview(summary: ReviewSummary, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 24.dp)) {
            Text(
                text = if (summary.hasReviews) "%.1f".format(summary.average) else "–",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (summary.hasReviews) {
                RatingStars(average = summary.average, starSize = 14.dp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = ratingCaption(summary),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (summary.isFailed) {
                TextButton(onClick = onRetry) {
                    Text("Spróbuj ponownie", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (summary.showHistogram) {
            Histogram(summary = summary, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Histogram(summary: ReviewSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        StarLevels.forEach { stars ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$stars",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                LinearProgressIndicator(
                    progress = { summary.shareOf(stars) },
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${summary.countsByStars[stars] ?: 0}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(review: ReviewModel, nowMillis: Long) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    review.reviewerName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row { repeat(review.rating) { StarIcon(size = 12) } }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(review.comment, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Dodano: " + RelativeTime.format(review.timestamp, nowMillis),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun StarIcon(size: Int) {
    Icon(
        imageVector = Icons.Default.Star,
        contentDescription = "Ocena",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(size.dp)
    )
}
