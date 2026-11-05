package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentSchedule
import com.example.stellare.ui.calendar.DayTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class DayTimelineTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Europe/Warsaw")

    private fun at(date: String, hour: Int, minute: Int = 0): Long =
        AppointmentSchedule.startsAtMillis(date, hour, minute, zone)

    private fun slot(id: String, date: String, hour: Int, minute: Int = 0) =
        AppointmentModel(appointmentId = id, startsAtMillis = at(date, hour, minute))

    @Test
    fun `pasek ma siedem dni od poniedzialku do niedzieli i zawiera podany dzien`() {
        val week = DayTimeline.weekOf(at("2026-10-08", 12), zone)
        assertEquals(7, week.size)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11), week.map { it.dayOfMonth })
        assertEquals("pon", week.first().label)
        assertEquals("nd", week.last().label)
    }

    @Test
    fun `blok o dziewiatej ma pelna dlugosc i nie jest przyciety`() {
        val blocks = DayTimeline.blocksFor(listOf(slot("a", "2026-10-08", 9)), at("2026-10-08", 0), zone)
        assertEquals(1, blocks.size)
        assertEquals(9 * 60, blocks[0].startMinuteOfDay)
        assertEquals(60, blocks[0].lengthMinutes)
        assertFalse(blocks[0].clippedAbove)
        assertFalse(blocks[0].clippedBelow)
    }

    @Test
    fun `termin przed oknem zostaje przyciety a nie wyrzucony`() {
        val blocks = DayTimeline.blocksFor(listOf(slot("wczesny", "2026-10-08", 6)), at("2026-10-08", 0), zone)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].clippedAbove)
    }

    @Test
    fun `termin o dwudziestej trzeciej zostaje przyciety od dolu`() {
        val blocks = DayTimeline.blocksFor(listOf(slot("pozny", "2026-10-08", 23)), at("2026-10-08", 0), zone)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].clippedBelow)
    }

    @Test
    fun `terminy z innego dnia nie trafiaja na os`() {
        val blocks = DayTimeline.blocksFor(listOf(slot("jutro", "2026-10-09", 10)), at("2026-10-08", 0), zone)
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `dwa terminy o tej samej godzinie dziela szerokosc`() {
        val blocks = DayTimeline.blocksFor(
            listOf(slot("a", "2026-10-08", 10), slot("b", "2026-10-08", 10)),
            at("2026-10-08", 0),
            zone
        )
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it.columns == 2 })
        assertEquals(setOf(0, 1), blocks.map { it.column }.toSet())
    }

    @Test
    fun `terminy rozlaczne zajmuja pelna szerokosc`() {
        val blocks = DayTimeline.blocksFor(
            listOf(slot("a", "2026-10-08", 10), slot("b", "2026-10-08", 12)),
            at("2026-10-08", 0),
            zone
        )
        assertTrue(blocks.all { it.columns == 1 && it.column == 0 })
    }

    @Test
    fun `bloki wracaja w kolejnosci rosnacej po czasie`() {
        val blocks = DayTimeline.blocksFor(
            listOf(slot("pozniej", "2026-10-08", 15), slot("wczesniej", "2026-10-08", 9)),
            at("2026-10-08", 0),
            zone
        )
        assertEquals(listOf("wczesniej", "pozniej"), blocks.map { it.appointmentId })
    }
}
