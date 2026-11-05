package com.example.stellare.ui.chat

import com.example.stellare.data.model.AppointmentSchedule
import com.example.stellare.data.model.MessageModel
import com.example.stellare.ui.ListKeys
import java.util.TimeZone

sealed interface ChatRow {
    data class DateSeparator(val label: String) : ChatRow
    data class Bubble(
        val message: MessageModel,
        val isOutgoing: Boolean,
        val isFirstOfGroup: Boolean,
        val isLastOfGroup: Boolean
    ) : ChatRow
}

object MessageGrouping {

    fun rowsFor(messages: List<MessageModel>, myId: String?, zone: TimeZone): List<ChatRow> {
        val rows = ArrayList<ChatRow>(messages.size * 2)
        messages.forEachIndexed { index, message ->
            val day = AppointmentSchedule.dateOf(message.timestamp, zone)
            val previous = messages.getOrNull(index - 1)
            val next = messages.getOrNull(index + 1)
            val startsNewDay = previous == null || AppointmentSchedule.dateOf(previous.timestamp, zone) != day
            val endsDay = next == null || AppointmentSchedule.dateOf(next.timestamp, zone) != day
            if (startsNewDay) rows.add(ChatRow.DateSeparator(AppointmentSchedule.displayDate(day)))
            rows.add(
                ChatRow.Bubble(
                    message = message,
                    isOutgoing = isMine(message, myId),
                    isFirstOfGroup = startsNewDay || previous?.senderId != message.senderId,
                    isLastOfGroup = endsDay || next?.senderId != message.senderId
                )
            )
        }
        return rows
    }

    fun keysFor(rows: List<ChatRow>): List<String> = ListKeys.unique(
        rows.map { row ->
            when (row) {
                is ChatRow.DateSeparator -> "day:${row.label}"
                is ChatRow.Bubble -> "msg:${row.message.messageId}"
            }
        }
    )

    private fun isMine(message: MessageModel, myId: String?): Boolean =
        !myId.isNullOrEmpty() && message.senderId == myId
}
