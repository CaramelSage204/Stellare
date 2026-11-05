package com.example.stellare.data.model

import java.util.Calendar
import java.util.TimeZone

object AppointmentSchedule {

    fun startsAtMillis(date: String, hour: Int, minute: Int, zone: TimeZone): Long {
        val parts = date.split("-")
        require(parts.size == 3) { "Data musi mieć postać rrrr-MM-dd: $date" }
        val year = requireNotNull(parts[0].toIntOrNull()) { "Nieprawidłowy rok: $date" }
        val month = requireNotNull(parts[1].toIntOrNull()) { "Nieprawidłowy miesiąc: $date" }
        val day = requireNotNull(parts[2].toIntOrNull()) { "Nieprawidłowy dzień: $date" }
        require(month in 1..12 && day in 1..31) { "Nieprawidłowa data: $date" }
        require(hour in 0..23 && minute in 0..59) { "Nieprawidłowa godzina: $hour:$minute" }
        return Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis
    }

    fun dateOf(startsAtMillis: Long, zone: TimeZone): String {
        val c = calendarAt(startsAtMillis, zone)
        return "${pad(c.get(Calendar.YEAR), 4)}-${pad(c.get(Calendar.MONTH) + 1, 2)}-${pad(c.get(Calendar.DAY_OF_MONTH), 2)}"
    }

    fun timeOf(startsAtMillis: Long, zone: TimeZone): String {
        val c = calendarAt(startsAtMillis, zone)
        return "${pad(c.get(Calendar.HOUR_OF_DAY), 2)}:${pad(c.get(Calendar.MINUTE), 2)}"
    }

    fun displayDate(isoDate: String): String {
        val parts = isoDate.split("-")
        return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else isoDate
    }

    private fun calendarAt(millis: Long, zone: TimeZone): Calendar =
        Calendar.getInstance(zone).apply { timeInMillis = millis }

    private fun pad(value: Int, width: Int): String = value.toString().padStart(width, '0')
}
