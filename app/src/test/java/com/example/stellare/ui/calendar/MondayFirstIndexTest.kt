package com.example.stellare.ui.calendar

import com.example.stellare.ui.calendar.components.mondayFirstIndex
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MondayFirstIndexTest {

    @Test
    fun `tydzien zaczyna sie w poniedzialek a konczy w niedziele`() {
        assertEquals("poniedziałek", 0, mondayFirstIndex(Calendar.MONDAY))
        assertEquals("wtorek", 1, mondayFirstIndex(Calendar.TUESDAY))
        assertEquals("środa", 2, mondayFirstIndex(Calendar.WEDNESDAY))
        assertEquals("czwartek", 3, mondayFirstIndex(Calendar.THURSDAY))
        assertEquals("piątek", 4, mondayFirstIndex(Calendar.FRIDAY))
        assertEquals("sobota", 5, mondayFirstIndex(Calendar.SATURDAY))
        assertEquals("niedziela, bo kalendarz Javy liczy ją jako pierwszą", 6, mondayFirstIndex(Calendar.SUNDAY))
    }
}
