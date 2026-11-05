package com.example.stellare.psychologistdetail

import com.example.stellare.ui.psychologistdetail.BookingActionsLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingActionsLayoutTest {

    @Test
    fun `przy duzej czcionce dwa przyciski ida jeden pod drugim, zeby nie lamac slow`() {
        assertTrue(
            "przy skali 1,5 dwa przyciski obok siebie łamią słowo w środku",
            BookingActionsLayout.stacked(1.5f, bothActionsShown = true)
        )
    }

    @Test
    fun `przy normalnej czcionce przyciski zostaja obok siebie`() {
        assertFalse("przy skali 1,0 jest miejsce na dwa przyciski w rzędzie", BookingActionsLayout.stacked(1.0f, true))
    }

    @Test
    fun `prog ukladania jeden pod drugim jest wlacznie`() {
        assertTrue("dokładnie na progu przyciski mają być pod sobą", BookingActionsLayout.stacked(BookingActionsLayout.STACK_FROM_FONT_SCALE, true))
    }

    @Test
    fun `jeden przycisk nigdy nie jest ukladany pod drugim`() {
        assertFalse("sam przycisk czatu zajmuje całą szerokość także w rzędzie", BookingActionsLayout.stacked(2.0f, bothActionsShown = false))
    }
}
