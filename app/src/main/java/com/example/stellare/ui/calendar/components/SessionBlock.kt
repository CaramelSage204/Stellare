package com.example.stellare.ui.calendar.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.ui.calendar.SessionTone

private data class SessionPalette(val container: Color, val content: Color, val border: Color, val pill: Color)

@Composable
private fun paletteOf(tone: SessionTone): SessionPalette {
    val colors = MaterialTheme.colorScheme
    return when (tone) {
        SessionTone.FREE -> SessionPalette(colors.surface, colors.primary, colors.primary, colors.primaryContainer)
        SessionTone.BOOKED -> SessionPalette(colors.primaryContainer, colors.onPrimaryContainer, colors.primaryContainer, colors.surface)
        SessionTone.COMPLETED -> SessionPalette(colors.surfaceVariant, colors.onSurfaceVariant, colors.outline, colors.surface)
    }
}

@Composable
fun SessionBlock(
    title: String,
    timeRange: String,
    tone: SessionTone,
    showRange: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = paletteOf(tone)
    Surface(
        modifier = modifier.clickable(onClickLabel = "Pokaż szczegóły terminu", onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = palette.container,
        contentColor = palette.content,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showRange) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = palette.pill,
                    contentColor = palette.content
                ) {
                    Text(
                        text = timeRange,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
