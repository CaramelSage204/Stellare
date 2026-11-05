package com.example.stellare.ui.auth

object PhoneNumber {
    private const val POLAND_CODE = "48"
    private const val POLAND_SUBSCRIBER_DIGITS = 9
    private const val MIN_DIGITS = 8
    private const val MAX_DIGITS = 15
    private const val SEPARATORS = "-()."

    fun normalize(input: String): String? {
        val compact = input.filterNot { it.isWhitespace() || it in SEPARATORS }
        val digits = when {
            compact.startsWith("+") -> compact.drop(1)
            compact.startsWith("00") -> compact.drop(2)
            else -> POLAND_CODE + compact
        }
        val acceptable = digits.all { it in '0'..'9' } &&
            digits.length in MIN_DIGITS..MAX_DIGITS &&
            (!digits.startsWith(POLAND_CODE) || digits.length == POLAND_CODE.length + POLAND_SUBSCRIBER_DIGITS)
        return if (acceptable) "+$digits" else null
    }
}
