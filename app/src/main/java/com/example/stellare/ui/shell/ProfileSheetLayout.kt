package com.example.stellare.ui.shell

object ProfileSheetLayout {
    const val DRAG_HANDLE_DP = 48f
    const val MIN_CONTENT_DP = 120f

    fun contentMaxHeightDp(screenHeightDp: Float, verticalInsetsDp: Float): Float =
        (screenHeightDp - verticalInsetsDp - DRAG_HANDLE_DP).coerceAtLeast(MIN_CONTENT_DP)
}
