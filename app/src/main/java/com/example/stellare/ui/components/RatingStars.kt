package com.example.stellare.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import com.example.stellare.data.model.MAX_STARS
import com.example.stellare.data.model.filledStars

@Composable
fun RatingStars(average: Double, starSize: Dp, modifier: Modifier = Modifier) {
    val filled = filledStars(average)
    Row(modifier = modifier.clearAndSetSemantics { contentDescription = "Ocena: $filled z $MAX_STARS gwiazdek" }) {
        repeat(MAX_STARS) { index ->
            Icon(
                imageVector = if (index < filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(starSize)
            )
        }
    }
}
