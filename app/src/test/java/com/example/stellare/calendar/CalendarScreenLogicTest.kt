package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentSchedule
import com.example.stellare.ui.calendar.AxisAppointments
import com.example.stellare.ui.calendar.BlockLane
import com.example.stellare.ui.calendar.CalendarDays
import com.example.stellare.ui.calendar.DayAgenda
import com.example.stellare.ui.calendar.SessionLabels
import com.example.stellare.ui.calendar.SessionTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class CalendarScreenLogicTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Europe/Warsaw")

    private fun at(date: String, hour: Int = 12, minute: Int = 0): Long =
        AppointmentSchedule.startsAtMillis(date, hour, minute, zone)

    private fun date(millis: Long): String = AppointmentSchedule.dateOf(millis, zone)

    private fun appointment(
        id: String,
        date: String,
        hour: Int,
        status: String = "BOOKED",
        patientId: String? = "pacjent"
    ) = AppointmentModel(
        appointmentId = id,
        psychologistId = "psycholog",
        patientId = patientId,
        startsAtMillis = at(date, hour),
        date = date,
        time = AppointmentSchedule.timeOf(at(date, hour), zone),
        status = status
    )

    private fun ids(entries: List<com.example.stellare.ui.calendar.AgendaEntry>) =
        entries.map { it.appointment.appointmentId }

    @Test
    fun `kolko dzis dotyczy zegara a nie dnia wybranego`() {
        val strip = CalendarDays.strip(selectedMillis = at("2026-10-14"), todayMillis = at("2026-10-08"), zone = zone)
        assertEquals(0, strip.count { it.isToday })
        assertEquals(listOf(14), strip.filter { it.isSelected }.map { it.day.dayOfMonth })
    }

    @Test
    fun `w tygodniu z dzisiaj dokladnie jeden dzien jest biezacy i jeden wybrany`() {
        val strip = CalendarDays.strip(at("2026-10-09"), at("2026-10-08"), zone)
        assertEquals(7, strip.size)
        assertEquals(listOf(8), strip.filter { it.isToday }.map { it.day.dayOfMonth })
        assertEquals(listOf(9), strip.filter { it.isSelected }.map { it.day.dayOfMonth })
    }

    @Test
    fun `nastepny tydzien zaczyna sie od poniedzialku`() {
        val target = CalendarDays.shiftWeek(at("2026-10-08"), forward = true, todayMillis = at("2026-10-08"), zone = zone)
        assertEquals("2026-10-12", date(target))
    }

    @Test
    fun `poprzedni tydzien zaczyna sie od poniedzialku`() {
        val target = CalendarDays.shiftWeek(at("2026-10-08"), forward = false, todayMillis = at("2026-10-08"), zone = zone)
        assertEquals("2026-09-28", date(target))
    }

    @Test
    fun `powrot do tygodnia z dzisiaj wybiera dzisiaj`() {
        val target = CalendarDays.shiftWeek(at("2026-10-13"), forward = false, todayMillis = at("2026-10-08"), zone = zone)
        assertEquals("2026-10-08", date(target))
    }

    @Test
    fun `przesuniecie tygodnia przez zmiane czasu na zimowy nie gubi dnia`() {
        val farFromToday = at("2026-01-01")
        assertEquals("2026-10-26", date(CalendarDays.shiftWeek(at("2026-10-25"), true, farFromToday, zone)))
        assertEquals("2026-10-19", date(CalendarDays.shiftWeek(at("2026-10-26"), false, farFromToday, zone)))
    }

    @Test
    fun `przesuniecie tygodnia przez zmiane czasu na letni nie gubi dnia`() {
        val farFromToday = at("2026-01-01")
        assertEquals("2026-03-30", date(CalendarDays.shiftWeek(at("2026-03-29"), true, farFromToday, zone)))
        assertEquals("2026-03-23", date(CalendarDays.shiftWeek(at("2026-03-30"), false, farFromToday, zone)))
    }

    @Test
    fun `po polnocy wybrany dzien dzisiejszy wedruje na nowy dzien`() {
        val followed = CalendarDays.followToday(
            selectedMillis = at("2026-10-08", 9),
            previousTodayMillis = at("2026-10-08", 23, 59),
            todayMillis = at("2026-10-09", 0, 1),
            zone = zone
        )
        assertEquals("2026-10-09", date(followed))
    }

    @Test
    fun `po polnocy swiadomie wybrany inny dzien zostaje`() {
        val selected = at("2026-10-14")
        val followed = CalendarDays.followToday(selected, at("2026-10-08", 23, 59), at("2026-10-09", 0, 1), zone)
        assertEquals(selected, followed)
    }

    @Test
    fun `w ciagu dnia wybor sie nie zmienia`() {
        val selected = at("2026-10-08", 9)
        assertEquals(selected, CalendarDays.followToday(selected, at("2026-10-08", 10), at("2026-10-08", 11), zone))
    }

    @Test
    fun `przeterminowany wybor z poprzedniej sesji skacze na dzisiaj`() {
        val stale = at("2026-10-07")
        assertEquals("2026-10-08", date(CalendarDays.followToday(stale, stale, at("2026-10-08", 8), zone)))
    }

    @Test
    fun `wybor przyszlego dnia przy wejsciu na ekran zostaje`() {
        val future = at("2026-10-14")
        assertEquals(future, CalendarDays.followToday(future, future, at("2026-10-08", 8), zone))
    }

    @Test
    fun `psycholog widzi zajete zakonczone i wolne ale nie odwolane`() {
        val all = listOf(
            appointment("b", "2026-10-08", 9, "BOOKED"),
            appointment("c", "2026-10-08", 10, "COMPLETED"),
            appointment("f", "2026-10-08", 11, "FREE", patientId = null),
            appointment("x", "2026-10-08", 12, "CANCELLED"),
            appointment("r", "2026-10-08", 13, "CANCELLED")
        )
        assertEquals(listOf("b", "c", "f"), AxisAppointments.forAxis(all, true, "psycholog").map { it.appointmentId })
    }

    @Test
    fun `pacjent widzi wylacznie swoje zajete i zakonczone`() {
        val all = listOf(
            appointment("moj", "2026-10-08", 9, "BOOKED", patientId = "ja"),
            appointment("moj-zakonczony", "2026-10-08", 10, "COMPLETED", patientId = "ja"),
            appointment("wolny", "2026-10-08", 11, "FREE", patientId = null),
            appointment("cudzy", "2026-10-08", 12, "BOOKED", patientId = "ktos"),
            appointment("odwolany", "2026-10-08", 13, "CANCELLED", patientId = "ja")
        )
        assertEquals(listOf("moj", "moj-zakonczony"), AxisAppointments.forAxis(all, false, "ja").map { it.appointmentId })
    }

    @Test
    fun `pacjent bez znanego identyfikatora nie widzi niczego`() {
        val all = listOf(appointment("a", "2026-10-08", 9, patientId = "ja"))
        assertTrue(AxisAppointments.forAxis(all, false, null).isEmpty())
    }

    @Test
    fun `termin calkowicie przed oknem trafia do wiersza wczesniej a nie na os`() {
        val agenda = DayAgenda.of(listOf(appointment("rano", "2026-10-08", 6)), at("2026-10-08", 0), zone)
        assertTrue(agenda.onAxis.isEmpty())
        assertEquals(listOf("rano"), ids(agenda.earlier))
        assertTrue(agenda.later.isEmpty())
    }

    @Test
    fun `termin calkowicie po oknie trafia do wiersza pozniej a nie na os`() {
        val agenda = DayAgenda.of(
            listOf(appointment("a", "2026-10-08", 20), appointment("b", "2026-10-08", 22)),
            at("2026-10-08", 0),
            zone
        )
        assertTrue(agenda.onAxis.isEmpty())
        assertEquals(listOf("a", "b"), ids(agenda.later))
    }

    @Test
    fun `termin o siodmej trzydziesci zostaje na osi bo widac jego czesc`() {
        val half = appointment("pol", "2026-10-08", 7).copy(startsAtMillis = at("2026-10-08", 7, 30))
        val agenda = DayAgenda.of(listOf(half), at("2026-10-08", 0), zone)
        assertEquals(1, agenda.onAxis.size)
        assertTrue(agenda.onAxis[0].block.clippedAbove)
        assertTrue(agenda.earlier.isEmpty())
    }

    @Test
    fun `wiersze poza osia sa chronologiczne i zaden termin nie znika`() {
        val all = listOf(
            appointment("o-21", "2026-10-08", 21),
            appointment("o-05", "2026-10-08", 5),
            appointment("o-10", "2026-10-08", 10),
            appointment("o-07", "2026-10-08", 7),
            appointment("o-02", "2026-10-08", 2)
        )
        val agenda = DayAgenda.of(all, at("2026-10-08", 0), zone)
        assertEquals(listOf("o-02", "o-05", "o-07"), ids(agenda.earlier))
        assertEquals(listOf("o-10"), ids(agenda.onAxis))
        assertEquals(listOf("o-21"), ids(agenda.later))
        assertEquals(all.size, agenda.earlier.size + agenda.onAxis.size + agenda.later.size)
    }

    @Test
    fun `pusty dzien jest pusty`() {
        assertTrue(DayAgenda.of(emptyList(), at("2026-10-08", 0), zone).isEmpty)
        assertFalse(DayAgenda.of(listOf(appointment("a", "2026-10-08", 10)), at("2026-10-08", 0), zone).isEmpty)
    }

    @Test
    fun `zakres godzin pokazuje poczatek i koniec sesji`() {
        assertEquals("10:00–11:00", SessionLabels.timeRange(appointment("a", "2026-10-08", 10), zone))
        assertEquals("23:00–00:00", SessionLabels.timeRange(appointment("a", "2026-10-08", 23), zone))
    }

    @Test
    fun `ton bloku zalezy od statusu`() {
        assertEquals(SessionTone.FREE, SessionLabels.toneOf(appointment("a", "2026-10-08", 9, "FREE")))
        assertEquals(SessionTone.BOOKED, SessionLabels.toneOf(appointment("a", "2026-10-08", 9, "BOOKED")))
        assertEquals(SessionTone.COMPLETED, SessionLabels.toneOf(appointment("a", "2026-10-08", 9, "COMPLETED")))
    }

    @Test
    fun `tytul bloku bierze nazwe drugiej strony albo neutralne zastepstwo`() {
        val booked = appointment("a", "2026-10-08", 9)
        assertEquals("Anna Nowak", SessionLabels.title(booked, true, mapOf("pacjent" to "Anna Nowak")))
        assertEquals("Pacjent", SessionLabels.title(booked, true, emptyMap()))
        assertEquals("Jan Kowal", SessionLabels.title(booked, false, mapOf("psycholog" to "Jan Kowal")))
        assertEquals("Psycholog", SessionLabels.title(booked, false, emptyMap()))
        assertEquals("Wolny termin", SessionLabels.title(appointment("f", "2026-10-08", 9, "FREE", null), true, emptyMap()))
    }

    @Test
    fun `do polnocy zostaje tyle ile brakuje do nowego dnia`() {
        assertEquals(60_000L, CalendarDays.millisUntilNextMidnight(at("2026-10-08", 23, 59), zone))
        assertEquals(24L * 3_600_000L, CalendarDays.millisUntilNextMidnight(at("2026-10-08", 0), zone))
    }

    @Test
    fun `do polnocy w dobie zmiany czasu liczy faktyczna dlugosc doby`() {
        assertEquals(25L * 3_600_000L, CalendarDays.millisUntilNextMidnight(at("2026-10-25", 0), zone))
        assertEquals(23L * 3_600_000L, CalendarDays.millisUntilNextMidnight(at("2026-03-29", 0), zone))
    }

    @Test
    fun `dwa terminy w tej samej godzinie dziela szerokosc po polowie bez zachodzenia`() {
        val agenda = DayAgenda.of(
            listOf(appointment("a", "2026-10-08", 10), appointment("b", "2026-10-08", 10)),
            at("2026-10-08", 0),
            zone
        )
        val blocks = agenda.onAxis.map { it.block }.sortedBy { it.column }
        assertEquals(listOf(0.5f, 0.5f), blocks.map { BlockLane.widthFraction(it) })
        assertEquals(listOf(0f, 0.5f), blocks.map { BlockLane.startFraction(it) })
    }

    @Test
    fun `trzy rownolegle terminy dziela szerokosc na trzy`() {
        val agenda = DayAgenda.of(
            listOf(appointment("a", "2026-10-08", 10), appointment("b", "2026-10-08", 10), appointment("c", "2026-10-08", 10)),
            at("2026-10-08", 0),
            zone
        )
        val blocks = agenda.onAxis.map { it.block }.sortedBy { it.column }
        assertTrue(blocks.all { BlockLane.widthFraction(it) == 1f / 3 })
        assertEquals(listOf(0f, 1f / 3, 2f / 3), blocks.map { BlockLane.startFraction(it) })
    }

    @Test
    fun `termin samotny zajmuje cala szerokosc`() {
        val block = DayAgenda.of(listOf(appointment("a", "2026-10-08", 10)), at("2026-10-08", 0), zone).onAxis[0].block
        assertEquals(1f, BlockLane.widthFraction(block))
        assertEquals(0f, BlockLane.startFraction(block))
    }
}
