package com.example.stellare.ui.calendar

import com.example.stellare.data.model.AppointmentModel
import java.util.TimeZone

data class AgendaEntry(val block: TimelineBlock, val appointment: AppointmentModel)

data class DayAgenda(
    val onAxis: List<AgendaEntry>,
    val earlier: List<AgendaEntry>,
    val later: List<AgendaEntry>
) {
    val isEmpty: Boolean get() = onAxis.isEmpty() && earlier.isEmpty() && later.isEmpty()

    companion object {
        fun of(appointments: List<AppointmentModel>, dayMillis: Long, zone: TimeZone): DayAgenda {
            val byId = appointments.associateBy { it.appointmentId }
            val entries = DayTimeline.blocksFor(appointments, dayMillis, zone).mapNotNull { block ->
                byId[block.appointmentId]?.let { AgendaEntry(block, it) }
            }
            val (visible, hidden) = entries.partition { it.block.lengthMinutes > 0 }
            val chronologicalHidden = hidden.sortedBy { it.appointment.startsAtMillis }
            return DayAgenda(
                onAxis = visible,
                earlier = chronologicalHidden.filter { it.block.clippedAbove },
                later = chronologicalHidden.filter { !it.block.clippedAbove && it.block.clippedBelow }
            )
        }
    }
}
