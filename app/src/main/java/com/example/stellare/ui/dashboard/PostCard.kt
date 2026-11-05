package com.example.stellare.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.stellare.data.model.PostModel
import com.example.stellare.ui.RelativeTime
import com.example.stellare.ui.theme.RatingStar
import com.example.stellare.ui.LocalClock

private const val AVATAR_SIZE = 56
private const val BADGE_OVERLAP = 12
private const val MAX_AVATAR_SCALE = 1.25f
private const val MAX_BADGE_SCALE = 2f
private const val INITIAL_FONT_SIZE = 22f
private val CardShape = RoundedCornerShape(28.dp)

@Composable
fun PostCard(
    post: PostModel,
    ratingText: String?,
    isAccented: Boolean,
    onClick: () -> Unit
) {
    val colors = postCardColors(isAccented)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.content),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            PostCardHeader(post, ratingText, colors)
            Spacer(modifier = Modifier.height(12.dp))
            Text(post.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.content)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = post.body,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
                color = colors.muted
            )
            if (post.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.tags.take(3).forEach { tag -> TagChip(tag, colors) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostCardHeader(post: PostModel, ratingText: String?, colors: PostCardColors) {
    Row(verticalAlignment = Alignment.Top) {
        AvatarWithRating(post, ratingText, colors)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${post.authorSnapshot.firstName} ${post.authorSnapshot.lastName}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.content
            )
            Text(
                text = RelativeTime.format(post.createdAt, LocalClock.current()),
                fontSize = 11.sp,
                color = colors.muted
            )
            val price = priceChipText(post)
            if (price != null || post.authorSnapshot.isVerified) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (price != null) PricePill(price, colors)
                    if (post.authorSnapshot.isVerified) VerifiedPill(colors)
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        ArrowBadge(colors)
    }
}

@Composable
private fun AvatarWithRating(post: PostModel, ratingText: String?, colors: PostCardColors) {
    val scale = LocalDensity.current.fontScale.coerceIn(1f, MAX_AVATAR_SCALE)
    val avatarSize = (AVATAR_SIZE * scale).dp
    val extra = if (ratingText != null) (BADGE_OVERLAP * LocalDensity.current.fontScale.coerceIn(1f, MAX_BADGE_SCALE)).dp else 0.dp
    Box(modifier = Modifier.width(avatarSize).height(avatarSize + extra)) {
        val imageUri = post.authorSnapshot.profileImageUri
        if (!imageUri.isNullOrEmpty()) {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                modifier = Modifier.size(avatarSize).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.size(avatarSize).clip(CircleShape).background(colors.softChip),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = post.authorSnapshot.firstName.firstOrNull()?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold,
                    fontSize = (INITIAL_FONT_SIZE * scale / LocalDensity.current.fontScale).sp,
                    color = colors.onSoftChip
                )
            }
        }
        if (ratingText != null) {
            RatingBadge(ratingText, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun RatingBadge(ratingText: String, modifier: Modifier) {
    Row(
        modifier = modifier
            .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
            .clearAndSetSemantics { contentDescription = "Ocena $ratingText" }
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Star, contentDescription = null, tint = RatingStar, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = ratingText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PricePill(text: String, colors: PostCardColors) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.strongChip)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = colors.onStrongChip
    )
}

@Composable
private fun VerifiedPill(colors: PostCardColors) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.softChip)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Verified, contentDescription = null, tint = colors.onSoftChip, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "Zweryfikowany", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onSoftChip, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ArrowBadge(colors: PostCardColors) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.softChip),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = colors.onSoftChip, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TagChip(tag: String, colors: PostCardColors) {
    Text(
        text = tag,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.softChip)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = colors.onSoftChip
    )
}
