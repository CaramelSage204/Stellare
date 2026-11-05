package com.example.stellare.ui.calendar

object BlockLane {

    fun widthFraction(block: TimelineBlock): Float = 1f / block.columns

    fun startFraction(block: TimelineBlock): Float = block.column.toFloat() / block.columns
}
