package com.example.stellare.ui.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentSchedule
import java.util.Calendar
import java.util.TimeZone

data class TimelineDay(
    val millis: Long,
    val label: String,
    val dayOfMonth: Int
)

data class TimelineBlock(
    val appointmentId: String,
    val startMinuteOfDay: Int,
    val lengthMinutes: Int,
    val column: Int,
    val columns: Int,
    val clippedAbove: Boolean,
    val clippedBelow: Boolean
)

object DayTimeline {

    const val VISIBLE_FROM_MINUTE = 8 * 60
    const val VISIBLE_TO_MINUTE = 20 * 60
    const val SESSION_MINUTES = 60

    private const val DAYS_IN_WEEK = 7
    private val dayLabels = listOf("pon", "wt", "śr", "czw", "pt", "sob", "nd")

    private class Placed(val appointmentId: String, val start: Int) {
        val end: Int get() = start + SESSION_MINUTES
        var column: Int = 0
    }

    fun weekOf(anyMillisInWeek: Long, zone: TimeZone): List<TimelineDay> {
        val cursor = startOfDay(anyMillisInWeek, zone)
        cursor.add(Calendar.DAY_OF_MONTH, -daysSinceMonday(cursor))
        return List(DAYS_IN_WEEK) { index ->
            val day = TimelineDay(
                millis = cursor.timeInMillis,
                label = dayLabels[index],
                dayOfMonth = cursor.get(Calendar.DAY_OF_MONTH)
            )
            cursor.add(Calendar.DAY_OF_MONTH, 1)
            day
        }
    }

    fun blocksFor(
        appointments: List<AppointmentModel>,
        dayMillis: Long,
        zone: TimeZone
    ): List<TimelineBlock> {
        val day = AppointmentSchedule.dateOf(dayMillis, zone)
        val placed = appointments
            .filter { AppointmentSchedule.dateOf(it.startsAtMillis, zone) == day }
            .map { Placed(it.appointmentId, minuteOfDay(it.startsAtMillis, zone)) }
            .sortedBy { it.start }
        return overlapGroups(placed).flatMap { group -> blocksOf(group) }
    }

    private fun blocksOf(group: List<Placed>): List<TimelineBlock> {
        val columns = assignColumns(group)
        return group.map { toBlock(it, columns) }
    }

    private fun toBlock(placed: Placed, columns: Int): TimelineBlock {
        val clippedStart = placed.start.coerceIn(VISIBLE_FROM_MINUTE, VISIBLE_TO_MINUTE)
        val clippedEnd = placed.end.coerceIn(VISIBLE_FROM_MINUTE, VISIBLE_TO_MINUTE)
        return TimelineBlock(
            appointmentId = placed.appointmentId,
            startMinuteOfDay = clippedStart,
            lengthMinutes = clippedEnd - clippedStart,
            column = placed.column,
            columns = columns,
            clippedAbove = placed.start < VISIBLE_FROM_MINUTE,
            clippedBelow = placed.end > VISIBLE_TO_MINUTE
        )
    }

    private fun overlapGroups(sorted: List<Placed>): List<List<Placed>> {
        val groups = mutableListOf<MutableList<Placed>>()
        var groupEnd = Int.MIN_VALUE
        for (item in sorted) {
            if (groups.isEmpty() || item.start >= groupEnd) {
                groups.add(mutableListOf(item))
                groupEnd = item.end
            } else {
                groups.last().add(item)
                groupEnd = maxOf(groupEnd, item.end)
            }
        }
        return groups
    }

    private fun assignColumns(group: List<Placed>): Int {
        val columnEnds = mutableListOf<Int>()
        for (item in group) {
            val free = columnEnds.indexOfFirst { it <= item.start }
            val column = if (free >= 0) free else columnEnds.size
            if (column == columnEnds.size) columnEnds.add(item.end) else columnEnds[column] = item.end
            item.column = column
        }
        return columnEnds.size
    }

    private fun minuteOfDay(millis: Long, zone: TimeZone): Int {
        val (hour, minute) = AppointmentSchedule.timeOf(millis, zone).split(":").map { it.toInt() }
        return hour * 60 + minute
    }

    private fun startOfDay(millis: Long, zone: TimeZone): Calendar =
        Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun daysSinceMonday(calendar: Calendar): Int =
        (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + DAYS_IN_WEEK) % DAYS_IN_WEEK
}
