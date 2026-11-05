package com.example.stellare.ui.calendar.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.ui.calendar.AgendaEntry
import com.example.stellare.ui.calendar.BlockLane
import com.example.stellare.ui.calendar.BlockRangeVisibility
import com.example.stellare.ui.calendar.DayAgenda
import com.example.stellare.ui.calendar.DayTimeline
import com.example.stellare.ui.calendar.SessionLabels
import java.util.TimeZone

private val HOUR_HEIGHT = 64.dp
private val BASE_LABEL_WIDTH = 48.dp
private val BASE_LABEL_HEIGHT = 16.dp
private const val MAX_LABEL_SCALE = 1.5f
private val BLOCK_GAP = 2.dp
private val MIN_HEIGHT_FOR_RANGE = 44.dp
private const val MINUTES_PER_HOUR = 60
private const val CONTEXT_HOURS_BELOW_FIRST = 2

@Composable
private fun labelScale(): Float = LocalDensity.current.fontScale.coerceIn(1f, MAX_LABEL_SCALE)

@Composable
private fun labelWidth(): Dp = BASE_LABEL_WIDTH * labelScale()

@Composable
private fun labelHeight(): Dp = BASE_LABEL_HEIGHT * labelScale()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HourAxis(
    agenda: DayAgenda,
    dayMillis: Long,
    zone: TimeZone,
    titleOf: (AppointmentModel) -> String,
    onSelect: (AppointmentModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val topRequester = remember { BringIntoViewRequester() }
    val firstBlockRequester = remember { BringIntoViewRequester() }
    val firstId = agenda.onAxis.firstOrNull()?.appointment?.appointmentId
    val contextPx = with(LocalDensity.current) { (HOUR_HEIGHT * CONTEXT_HOURS_BELOW_FIRST).toPx() }
    LaunchedEffect(dayMillis, firstId) {
        if (firstId == null) {
            topRequester.bringIntoView()
        } else {
            firstBlockRequester.bringIntoView(Rect(0f, 0f, 1f, contextPx))
        }
    }
    Column(
        modifier = modifier.fillMaxWidth().bringIntoViewRequester(topRequester),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OffAxisGroup("Wcześniej tego dnia", agenda.earlier, zone, titleOf, onSelect)
        AxisBody(agenda.onAxis, zone, firstBlockRequester, titleOf, onSelect)
        OffAxisGroup("Później tego dnia", agenda.later, zone, titleOf, onSelect)
        if (agenda.isEmpty) {
            Text(
                text = "Brak terminów w tym dniu.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = labelWidth(), bottom = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AxisBody(
    entries: List<AgendaEntry>,
    zone: TimeZone,
    firstBlockRequester: BringIntoViewRequester,
    titleOf: (AppointmentModel) -> String,
    onSelect: (AppointmentModel) -> Unit
) {
    val firstHour = DayTimeline.VISIBLE_FROM_MINUTE / MINUTES_PER_HOUR
    val lastHour = DayTimeline.VISIBLE_TO_MINUTE / MINUTES_PER_HOUR
    val dpPerMinute = HOUR_HEIGHT / MINUTES_PER_HOUR
    val totalHeight = HOUR_HEIGHT * (lastHour - firstHour) + labelHeight()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
        val laneWidth = maxWidth - labelWidth()
        for (hour in firstHour..lastHour) {
            val top = HOUR_HEIGHT * (hour - firstHour)
            Text(
                text = SessionLabels.hourLabel(hour),
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(y = top).width(labelWidth())
            )
            HorizontalDivider(
                modifier = Modifier.offset(x = labelWidth(), y = top + labelHeight() / 2).width(laneWidth),
                color = MaterialTheme.colorScheme.outline
            )
        }
        entries.forEachIndexed { index, entry ->
            val block = entry.block
            val top = dpPerMinute * (block.startMinuteOfDay - DayTimeline.VISIBLE_FROM_MINUTE) + labelHeight() / 2
            val height = dpPerMinute * block.lengthMinutes - BLOCK_GAP
            val columnWidth = laneWidth * BlockLane.widthFraction(block)
            SessionBlock(
                title = titleOf(entry.appointment),
                timeRange = SessionLabels.timeRange(entry.appointment, zone),
                tone = SessionLabels.toneOf(entry.appointment),
                showRange = BlockRangeVisibility.showsRange(height.value, MIN_HEIGHT_FOR_RANGE.value, LocalDensity.current.fontScale),
                onClick = { onSelect(entry.appointment) },
                modifier = Modifier
                    .offset(x = labelWidth() + laneWidth * BlockLane.startFraction(block), y = top)
                    .width(columnWidth - BLOCK_GAP)
                    .height(height)
                    .then(if (index == 0) Modifier.bringIntoViewRequester(firstBlockRequester) else Modifier)
            )
        }
    }
}

@Composable
private fun OffAxisGroup(
    heading: String,
    entries: List<AgendaEntry>,
    zone: TimeZone,
    titleOf: (AppointmentModel) -> String,
    onSelect: (AppointmentModel) -> Unit
) {
    if (entries.isEmpty()) return
    Column(
        modifier = Modifier.padding(start = labelWidth()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = heading,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        entries.forEach { entry ->
            SessionBlock(
                title = "${SessionLabels.timeRange(entry.appointment, zone)} · ${titleOf(entry.appointment)}",
                timeRange = "",
                tone = SessionLabels.toneOf(entry.appointment),
                showRange = false,
                onClick = { onSelect(entry.appointment) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
