package com.example.stellare.ui

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object RelativeTime {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY

    fun format(thenMillis: Long, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): String {
        if (thenMillis <= 0L) return "Brak daty"
        val diff = nowMillis - thenMillis
        return when {
            diff < MINUTE -> "przed chwilą"
            diff < HOUR -> ago(diff / MINUTE, "minutę", "minuty", "minut")
            diff < DAY -> ago(diff / HOUR, "godzinę", "godziny", "godzin")
            diff < 2 * DAY -> "wczoraj"
            diff < WEEK -> "${diff / DAY} dni temu"
            else -> SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("pl-PL"))
                .apply { timeZone = zone }
                .format(thenMillis)
        }
    }

    private fun ago(count: Long, one: String, few: String, many: String): String =
        "$count ${PolishPlural.form(count.toInt(), one, few, many)} temu"
}
