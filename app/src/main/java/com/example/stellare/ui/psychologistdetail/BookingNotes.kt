package com.example.stellare.ui.psychologistdetail

import com.example.stellare.ui.calendar.BookingState

object BookingNotes {

    fun afterOutcome(state: BookingState, current: String): String =
        if (state is BookingState.Success) "" else current
}
