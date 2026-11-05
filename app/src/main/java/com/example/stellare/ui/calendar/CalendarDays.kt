package com.example.stellare.ui.calendar

import com.example.stellare.data.model.AppointmentSchedule
import java.util.Calendar
import java.util.TimeZone

data class StripDay(val day: TimelineDay, val isSelected: Boolean, val isToday: Boolean)

object CalendarDays {

    private const val HALF_DAY_MS = 12 * 60 * 60 * 1000L
    private const val DAY_AND_HALF_MS = 36 * 60 * 60 * 1000L

    fun isSameDay(first: Long, second: Long, zone: TimeZone): Boolean =
        AppointmentSchedule.dateOf(first, zone) == AppointmentSchedule.dateOf(second, zone)

    fun strip(selectedMillis: Long, todayMillis: Long, zone: TimeZone): List<StripDay> =
        DayTimeline.weekOf(selectedMillis, zone).map { day ->
            StripDay(
                day = day,
                isSelected = isSameDay(day.millis, selectedMillis, zone),
                isToday = isSameDay(day.millis, todayMillis, zone)
            )
        }

    fun shiftWeek(selectedMillis: Long, forward: Boolean, todayMillis: Long, zone: TimeZone): Long {
        val week = DayTimeline.weekOf(selectedMillis, zone)
        val anchor = if (forward) week.last().millis + DAY_AND_HALF_MS else week.first().millis - HALF_DAY_MS
        val target = DayTimeline.weekOf(anchor, zone)
        return target.firstOrNull { isSameDay(it.millis, todayMillis, zone) }?.millis ?: target.first().millis
    }

    fun followToday(selectedMillis: Long, previousTodayMillis: Long, todayMillis: Long, zone: TimeZone): Long {
        val selected = AppointmentSchedule.dateOf(selectedMillis, zone)
        val previous = AppointmentSchedule.dateOf(previousTodayMillis, zone)
        val today = AppointmentSchedule.dateOf(todayMillis, zone)
        return if (selected == previous && today > previous) todayMillis else selectedMillis
    }

    fun millisUntilNextMidnight(nowMillis: Long, zone: TimeZone): Long {
        val midnight = Calendar.getInstance(zone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }
        return midnight.timeInMillis - nowMillis
    }
}
