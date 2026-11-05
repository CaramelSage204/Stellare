package com.example.stellare.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

const val DisabledButtonContentAlpha = 0.75f

private val ControlOutlineWidth = 1.dp

fun ColorScheme.disabledButtonContainer(): Color = surfaceVariant

fun ColorScheme.disabledButtonContent(): Color =
    onSurfaceVariant.copy(alpha = DisabledButtonContentAlpha)

@Composable
fun stellareButtonColors(
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
): ButtonColors {
    val scheme = MaterialTheme.colorScheme
    return ButtonDefaults.buttonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = scheme.disabledButtonContainer(),
        disabledContentColor = scheme.disabledButtonContent()
    )
}

@Composable
fun stellareTextButtonColors(
    contentColor: Color = MaterialTheme.colorScheme.primary
): ButtonColors = ButtonDefaults.textButtonColors(
    contentColor = contentColor,
    disabledContentColor = MaterialTheme.colorScheme.disabledButtonContent()
)

@Composable
fun stellareOutlinedButtonColors(
    contentColor: Color = MaterialTheme.colorScheme.primary
): ButtonColors = ButtonDefaults.outlinedButtonColors(
    contentColor = contentColor,
    disabledContentColor = MaterialTheme.colorScheme.disabledButtonContent()
)

@Composable
fun stellareIconButtonColors(
    contentColor: Color = LocalContentColor.current
): IconButtonColors = IconButtonDefaults.iconButtonColors(
    contentColor = contentColor,
    disabledContentColor = MaterialTheme.colorScheme.disabledButtonContent()
)

val MaterialTheme.controlOutline: Color
    @Composable
    @ReadOnlyComposable
    get() = if (colorScheme.background.luminance() < 0.5f) ControlOutlineDark else ControlOutlineLight

/**
 * Obramowanie jest granicą kontrolki, więc podlega wymaganiu 3:1 tak samo jak jej treść.
 * Domyślne obramowanie Material 3 dawało zmierzone 1,33:1 na tle i 1,08:1 na karcie, a w stanie
 * wyłączonym (alfa 0,12) nie było widać go wcale — wyłączony przycisk przestawał mieć kształt.
 * Stan wyłączony rysuje się więc tym samym kolorem co treść wyłączona: kontrolka nadal ma granicę,
 * a o jej nieczynności mówi kolor napisu.
 */
@Composable
fun stellareOutlinedButtonBorder(enabled: Boolean = true): BorderStroke = BorderStroke(
    width = ControlOutlineWidth,
    color = if (enabled) MaterialTheme.controlOutline else MaterialTheme.colorScheme.disabledButtonContent()
)
