package com.example.stellare.ui.psychologistdetail

object BookingActionsLayout {
    const val STACK_FROM_FONT_SCALE = 1.2f

    fun stacked(fontScale: Float, bothActionsShown: Boolean): Boolean =
        bothActionsShown && fontScale >= STACK_FROM_FONT_SCALE
}
