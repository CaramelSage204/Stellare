package com.example.stellare.ui

object PolishPlural {

    fun form(count: Int, one: String, few: String, many: String): String {
        val n = kotlin.math.abs(count)
        val lastTwo = n % 100
        val last = n % 10
        return when {
            n == 1 -> one
            last in 2..4 && lastTwo !in 12..14 -> few
            else -> many
        }
    }

    fun opinions(count: Int): String =
        "$count ${form(count, "opinia", "opinie", "opinii")}"

}
