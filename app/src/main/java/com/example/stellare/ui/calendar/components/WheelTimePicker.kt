package com.example.stellare.ui.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val MAX_WHEEL_SCALE = 1.6f

@Composable
fun WheelTimePicker(
    selectedHour: Int,
    selectedMinute: Int,
    onTimeSelected: (hour: Int, minute: Int) -> Unit
) {
    val scale = LocalDensity.current.fontScale.coerceIn(1f, MAX_WHEEL_SCALE)
    val hours = (0..23).map { it.toString().padStart(2, '0') }
    val minutes = (0..59 step 5).map { it.toString().padStart(2, '0') }
    
    val hoursListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedHour)
    val minutesListState = rememberLazyListState(initialFirstVisibleItemIndex = minutes.indexOf(selectedMinute.toString().padStart(2, '0')).coerceAtLeast(0))
    
    val coroutineScope = rememberCoroutineScope()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp * scale)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text("Godzina", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            
            Box(
                modifier = Modifier
                    .height(110.dp * scale)
                    .width(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp * scale)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                )
                
                LazyColumn(
                    state = hoursListState,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = hoursListState),
                    contentPadding = PaddingValues(vertical = 38.dp * scale),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(hours) { index, hour ->
                        val isSelected = hoursListState.firstVisibleItemIndex == index
                        
                        Box(
                            modifier = Modifier
                                .height(34.dp * scale)
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        hoursListState.animateScrollToItem(index)
                                        val mIdx = minutesListState.firstVisibleItemIndex.coerceIn(0, minutes.size - 1)
                                        onTimeSelected(index, minutes[mIdx].toInt())
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = hour,
                                fontSize = if (isSelected) 18.sp else 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
        
        Text(
            text = ":",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text("Minuta", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            
            Box(
                modifier = Modifier
                    .height(110.dp * scale)
                    .width(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp * scale)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                )
                
                LazyColumn(
                    state = minutesListState,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = minutesListState),
                    contentPadding = PaddingValues(vertical = 38.dp * scale),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(minutes) { index, minute ->
                        val isSelected = minutesListState.firstVisibleItemIndex == index
                        
                        Box(
                            modifier = Modifier
                                .height(34.dp * scale)
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        minutesListState.animateScrollToItem(index)
                                        val hIdx = hoursListState.firstVisibleItemIndex.coerceIn(0, hours.size - 1)
                                        onTimeSelected(hIdx, minute.toInt())
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = minute,
                                fontSize = if (isSelected) 18.sp else 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
    
    LaunchedEffect(hoursListState.isScrollInProgress, minutesListState.isScrollInProgress) {
        if (!hoursListState.isScrollInProgress && !minutesListState.isScrollInProgress) {
            val hIdx = hoursListState.firstVisibleItemIndex.coerceIn(0, hours.size - 1)
            val mIdx = minutesListState.firstVisibleItemIndex.coerceIn(0, minutes.size - 1)
            onTimeSelected(hIdx, minutes[mIdx].toInt())
        }
    }
}
