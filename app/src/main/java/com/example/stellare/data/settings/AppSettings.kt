package com.example.stellare.data.settings

enum class ThemeChoice {
    SYSTEM,
    LIGHT,
    DARK;

    fun useDarkTheme(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStored(raw: String?): ThemeChoice = enumOrDefault(raw, SYSTEM)
    }
}

enum class FontScaleChoice(val multiplier: Float) {
    NORMAL(1.0f),
    LARGE(1.15f),
    LARGEST(1.3f);

    companion object {
        fun fromStored(raw: String?): FontScaleChoice = enumOrDefault(raw, NORMAL)
    }
}

enum class BubbleColorChoice {
    PEACH,
    GREEN,
    BLUE,
    NEUTRAL;

    companion object {
        fun fromStored(raw: String?): BubbleColorChoice = enumOrDefault(raw, PEACH)
    }
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val fontScale: FontScaleChoice = FontScaleChoice.NORMAL,
    val bubbleColor: BubbleColorChoice = BubbleColorChoice.PEACH
)

const val MAX_TESTED_FONT_SCALE = 2.0f

fun effectiveFontScale(systemScale: Float, choice: FontScaleChoice): Float {
    val scaled = (systemScale * choice.multiplier).coerceAtMost(MAX_TESTED_FONT_SCALE)
    return maxOf(systemScale, scaled)
}

private inline fun <reified E : Enum<E>> enumOrDefault(raw: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == raw } ?: default
