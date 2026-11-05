package com.example.stellare.calendar

import com.example.stellare.ui.calendar.BookingState
import com.example.stellare.ui.psychologistdetail.BookingNotes
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingNotesTest {

    @Test
    fun `udana rezerwacja czysci notatke, zeby nie wrocila przy nastepnej`() {
        assertEquals(
            "notatka z zakończonej rezerwacji ma zniknąć, inaczej wróci w następnej",
            "",
            BookingNotes.afterOutcome(BookingState.Success, "Boli mnie glowa")
        )
    }

    @Test
    fun `blad rezerwacji zachowuje notatke, zeby nie trzeba bylo pisac od nowa`() {
        assertEquals(
            "po błędzie notatka ma zostać",
            "Boli mnie glowa",
            BookingNotes.afterOutcome(BookingState.Error("brak monet"), "Boli mnie glowa")
        )
    }

    @Test
    fun `w toku i bezczynnosc nie ruszaja notatki`() {
        assertEquals("w toku notatka ma zostać", "x", BookingNotes.afterOutcome(BookingState.InProgress, "x"))
        assertEquals("bezczynność nie rusza notatki", "x", BookingNotes.afterOutcome(BookingState.Idle, "x"))
    }
}
