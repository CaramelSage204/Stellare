package com.example.stellare.ui.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.ui.calendar.StripDay

/**
 * Strzałki tygodnia stoją w wierszu z datą ([DayNavigator]), a nie tutaj. Dwa przyciski ikonowe
 * zabierały z paska 96 dp nienegocjowalnej szerokości (minimalny cel dotyku), przez co na wąskim
 * ekranie na siedem dni zostawało po ~27 dp i trzyliterowe nazwy się nie mieściły.
 */
@Composable
fun DayStrip(
    days: List<StripDay>,
    onSelectDay: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        days.forEach { stripDay ->
            DayCell(
                stripDay = stripDay,
                onClick = { onSelectDay(stripDay.day.millis) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Komórki dzielą szerokość po równo ([weight]), bo bez tego wcześniejsze dni zabierały całą
 * dostępną szerokość, a ostatnie dostawały jej resztkę: przy wąskim ekranie (zmierzone: gęstość
 * 546, skala 1,4) nazwa dnia łamała się na dwa wiersze, kółko zawijało cyfry pionowo, a siódmy
 * dzień wypadał z widoku. [maxLines] pilnuje, by zabrakło miejsca w poziomie, a nie w pionie.
 */
@Composable
private fun DayCell(stripDay: StripDay, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val day = stripDay.day
    val colors = MaterialTheme.colorScheme
    val description = if (stripDay.isToday) "${day.label} ${day.dayOfMonth}, dziś" else "${day.label} ${day.dayOfMonth}"
    Column(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = stripDay.isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.label,
            fontSize = 11.sp,
            maxLines = 1,
            color = colors.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 34.dp, minHeight = 34.dp)
                .clip(CircleShape)
                .background(if (stripDay.isSelected) colors.primary else colors.surface)
                .border(
                    width = if (stripDay.isToday) 1.5.dp else 0.dp,
                    color = if (stripDay.isToday) colors.primary else colors.surface,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                fontSize = 14.sp,
                maxLines = 1,
                fontWeight = if (stripDay.isSelected || stripDay.isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (stripDay.isSelected) colors.onPrimary else colors.onSurface
            )
        }
    }
}
