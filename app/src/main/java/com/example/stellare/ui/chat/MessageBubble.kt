package com.example.stellare.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.data.model.MessageModel
import com.example.stellare.data.settings.BubbleColorChoice
import com.example.stellare.ui.theme.BubbleBlueDark
import com.example.stellare.ui.theme.BubbleBlueLight
import com.example.stellare.ui.theme.BubbleNeutralDark
import com.example.stellare.ui.theme.BubbleNeutralLight
import com.example.stellare.ui.theme.LocalDarkTheme
import com.example.stellare.ui.theme.OnBubbleBlueDark
import com.example.stellare.ui.theme.OnBubbleBlueLight
import com.example.stellare.ui.theme.OnBubbleNeutralDark
import com.example.stellare.ui.theme.OnBubbleNeutralLight

data class BubbleColors(val container: Color, val content: Color, val border: Color)

fun bubbleColorsFor(
    scheme: ColorScheme,
    darkTheme: Boolean,
    choice: BubbleColorChoice,
    isOutgoing: Boolean
): BubbleColors {
    val border = if (darkTheme) scheme.outline else scheme.secondary
    val fill = fillColors(scheme, darkTheme, choice, isOutgoing)
    return BubbleColors(fill.first, fill.second, border)
}

private fun fillColors(
    scheme: ColorScheme,
    darkTheme: Boolean,
    choice: BubbleColorChoice,
    isOutgoing: Boolean
): Pair<Color, Color> {
    if (!isOutgoing) return scheme.surfaceVariant to scheme.onSurfaceVariant
    return when (choice) {
        BubbleColorChoice.PEACH -> scheme.primaryContainer to scheme.onPrimaryContainer
        BubbleColorChoice.GREEN -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        BubbleColorChoice.BLUE ->
            if (darkTheme) BubbleBlueDark to OnBubbleBlueDark else BubbleBlueLight to OnBubbleBlueLight
        BubbleColorChoice.NEUTRAL ->
            if (darkTheme) BubbleNeutralDark to OnBubbleNeutralDark else BubbleNeutralLight to OnBubbleNeutralLight
    }
}

@Composable
fun bubbleColors(isOutgoing: Boolean, choice: BubbleColorChoice): BubbleColors =
    bubbleColorsFor(MaterialTheme.colorScheme, LocalDarkTheme.current, choice, isOutgoing)

private val BubbleRadius = 16.dp
private val TailRadius = 2.dp
private val GroupGap = 8.dp

@Composable
fun MessageBubble(
    message: MessageModel,
    isOutgoing: Boolean,
    isFirstOfGroup: Boolean,
    isLastOfGroup: Boolean,
    bubbleColor: BubbleColorChoice
) {
    val colors = bubbleColors(isOutgoing, bubbleColor)
    val tail = if (isLastOfGroup) TailRadius else BubbleRadius
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = if (isFirstOfGroup) GroupGap else 0.dp),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = BubbleRadius,
                topEnd = BubbleRadius,
                bottomStart = if (isOutgoing) BubbleRadius else tail,
                bottomEnd = if (isOutgoing) tail else BubbleRadius
            ),
            colors = CardDefaults.cardColors(containerColor = colors.container),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = message.text, color = colors.content, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun DateSeparatorLabel(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
