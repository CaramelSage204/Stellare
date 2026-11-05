package com.example.stellare.ui.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

@Immutable
data class PostCardColors(
    val container: Color,
    val content: Color,
    val muted: Color,
    val strongChip: Color,
    val onStrongChip: Color,
    val softChip: Color,
    val onSoftChip: Color
)

@Composable
@ReadOnlyComposable
fun postCardColors(isAccented: Boolean): PostCardColors {
    val scheme = MaterialTheme.colorScheme
    return if (isAccented) {
        PostCardColors(
            container = scheme.inverseSurface,
            content = scheme.inverseOnSurface,
            muted = scheme.inverseOnSurface.copy(alpha = 0.72f),
            strongChip = scheme.inverseOnSurface,
            onStrongChip = scheme.inverseSurface,
            softChip = scheme.inverseOnSurface.copy(alpha = 0.16f),
            onSoftChip = scheme.inverseOnSurface
        )
    } else {
        PostCardColors(
            container = scheme.surfaceVariant,
            content = scheme.onSurface,
            muted = scheme.onSurfaceVariant,
            strongChip = scheme.primary,
            onStrongChip = scheme.onPrimary,
            softChip = scheme.surface,
            onSoftChip = scheme.primary
        )
    }
}
