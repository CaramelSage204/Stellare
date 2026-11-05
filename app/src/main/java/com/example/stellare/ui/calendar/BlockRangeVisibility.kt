package com.example.stellare.ui.calendar

object BlockRangeVisibility {

    fun showsRange(blockHeightDp: Float, minHeightDp: Float, fontScale: Float): Boolean =
        blockHeightDp >= minHeightDp * fontScale.coerceAtLeast(1f)
}
