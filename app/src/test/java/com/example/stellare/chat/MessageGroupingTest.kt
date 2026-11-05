package com.example.stellare.chat

import com.example.stellare.data.model.MessageModel
import com.example.stellare.ui.chat.ChatRow
import com.example.stellare.ui.chat.MessageGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MessageGroupingTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Europe/Warsaw")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun msg(sender: String, time: Long, text: String = "t") =
        MessageModel(messageId = "$sender-$time", chatId = "c", senderId = sender, text = text, timestamp = time)

    private fun bubbles(rows: List<ChatRow>) = rows.filterIsInstance<ChatRow.Bubble>()
    private fun separators(rows: List<ChatRow>) = rows.filterIsInstance<ChatRow.DateSeparator>()

    @Test
    fun `pusta lista daje pusty wynik`() {
        assertEquals(emptyList<ChatRow>(), MessageGrouping.rowsFor(emptyList(), "ja", zone))
    }

    @Test
    fun `pierwsza wiadomosc jest poprzedzona separatorem dnia`() {
        val rows = MessageGrouping.rowsFor(listOf(msg("ja", at(2026, 10, 7, 9))), "ja", zone)

        assertEquals(2, rows.size)
        assertEquals(ChatRow.DateSeparator("07.10.2026"), rows[0])
        assertTrue(rows[1] is ChatRow.Bubble)
    }

    @Test
    fun `separator pojawia sie raz na dzien`() {
        val rows = MessageGrouping.rowsFor(
            listOf(
                msg("ja", at(2026, 10, 7, 9)),
                msg("ja", at(2026, 10, 7, 10)),
                msg("ja", at(2026, 10, 7, 23))
            ),
            "ja", zone
        )

        assertEquals(listOf(ChatRow.DateSeparator("07.10.2026")), separators(rows))
        assertEquals(3, bubbles(rows).size)
    }

    @Test
    fun `zmiana dnia wstawia nowy separator przed pierwsza wiadomoscia nowego dnia`() {
        val rows = MessageGrouping.rowsFor(
            listOf(msg("ja", at(2026, 10, 7, 23, 50)), msg("ja", at(2026, 10, 8, 0, 10))),
            "ja", zone
        )

        assertEquals(
            listOf(ChatRow.DateSeparator("07.10.2026"), ChatRow.DateSeparator("08.10.2026")),
            separators(rows)
        )
        assertTrue(rows[2] is ChatRow.DateSeparator)
    }

    @Test
    fun `dzien jest liczony w podanej strefie a nie w UTC`() {
        val justAfterMidnight = at(2026, 10, 8, 0, 30)
        val rows = MessageGrouping.rowsFor(listOf(msg("ja", justAfterMidnight)), "ja", zone)

        assertEquals(ChatRow.DateSeparator("08.10.2026"), rows[0])
    }

    @Test
    fun `kolejne wiadomosci tego samego autora tworza jedna grupe`() {
        val rows = MessageGrouping.rowsFor(
            listOf(
                msg("ja", at(2026, 10, 7, 9, 0)),
                msg("ja", at(2026, 10, 7, 9, 1)),
                msg("ja", at(2026, 10, 7, 9, 2))
            ),
            "ja", zone
        )
        val b = bubbles(rows)

        assertEquals(listOf(true, false, false), b.map { it.isFirstOfGroup })
        assertEquals(listOf(false, false, true), b.map { it.isLastOfGroup })
    }

    @Test
    fun `zmiana autora konczy grupe i zaczyna nastepna`() {
        val rows = MessageGrouping.rowsFor(
            listOf(
                msg("ja", at(2026, 10, 7, 9, 0)),
                msg("on", at(2026, 10, 7, 9, 1)),
                msg("ja", at(2026, 10, 7, 9, 2))
            ),
            "ja", zone
        )
        val b = bubbles(rows)

        assertEquals(listOf(true, true, true), b.map { it.isFirstOfGroup })
        assertEquals(listOf(true, true, true), b.map { it.isLastOfGroup })
    }

    @Test
    fun `pojedyncza wiadomosc jest jednoczesnie pierwsza i ostatnia w grupie`() {
        val b = bubbles(MessageGrouping.rowsFor(listOf(msg("on", at(2026, 10, 7, 9))), "ja", zone)).single()

        assertTrue(b.isFirstOfGroup)
        assertTrue(b.isLastOfGroup)
    }

    @Test
    fun `granica dnia przerywa grupe tego samego autora`() {
        val b = bubbles(
            MessageGrouping.rowsFor(
                listOf(msg("ja", at(2026, 10, 7, 23, 59)), msg("ja", at(2026, 10, 8, 0, 1))),
                "ja", zone
            )
        )

        assertTrue(b[0].isLastOfGroup)
        assertTrue(b[1].isFirstOfGroup)
    }

    @Test
    fun `moja wiadomosc jest wychodzaca a cudza przychodzaca`() {
        val b = bubbles(
            MessageGrouping.rowsFor(
                listOf(msg("ja", at(2026, 10, 7, 9, 0)), msg("on", at(2026, 10, 7, 9, 1))),
                "ja", zone
            )
        )

        assertTrue(b[0].isOutgoing)
        assertFalse(b[1].isOutgoing)
    }

    @Test
    fun `bez zalogowanego uzytkownika zadna wiadomosc nie jest wychodzaca`() {
        val b = bubbles(MessageGrouping.rowsFor(listOf(msg("ja", at(2026, 10, 7, 9))), null, zone))

        assertFalse(b.single().isOutgoing)
    }

    @Test
    fun `kolejnosc wierszy odpowiada kolejnosci wiadomosci`() {
        val first = msg("ja", at(2026, 10, 7, 9), "pierwsza")
        val second = msg("on", at(2026, 10, 7, 9, 5), "druga")
        val b = bubbles(MessageGrouping.rowsFor(listOf(first, second), "ja", zone))

        assertEquals(listOf(first, second), b.map { it.message })
    }

    @Test
    fun `klucze wierszy sa unikalne takze gdy dwie wiadomosci maja ten sam messageId`() {
        val day = at(2026, 10, 7, 9)
        val a = MessageModel(messageId = "dup", chatId = "c", senderId = "ja", text = "a", timestamp = day)
        val b = MessageModel(messageId = "dup", chatId = "c", senderId = "on", text = "b", timestamp = day + 60_000)
        val blank1 = MessageModel(messageId = "", chatId = "c", senderId = "on", text = "c", timestamp = day + 120_000)
        val blank2 = MessageModel(messageId = "", chatId = "c", senderId = "on", text = "d", timestamp = day + 180_000)

        val rows = MessageGrouping.rowsFor(listOf(a, b, blank1, blank2), "ja", zone)
        val keys = MessageGrouping.keysFor(rows)

        assertEquals("klucz na wiersz", rows.size, keys.size)
        assertEquals("zduplikowane klucze zawiesiłyby LazyColumn: $keys", keys.size, keys.toSet().size)
    }

    @Test
    fun `klucz istniejacego wiersza nie zmienia sie po dopisaniu nowej wiadomosci na koncu`() {
        val first = msg("ja", at(2026, 10, 7, 9))
        val second = msg("on", at(2026, 10, 7, 9, 5))
        val third = msg("ja", at(2026, 10, 7, 9, 10))

        val before = MessageGrouping.keysFor(MessageGrouping.rowsFor(listOf(first, second), "ja", zone))
        val after = MessageGrouping.keysFor(MessageGrouping.rowsFor(listOf(first, second, third), "ja", zone))

        assertEquals(before, after.take(before.size))
    }

    @Test
    fun `klucz wiadomosci wynika z jej identyfikatora a nie z pozycji na liscie`() {
        val older = msg("on", at(2026, 10, 7, 8))
        val newer = msg("ja", at(2026, 10, 7, 9))

        val alone = MessageGrouping.rowsFor(listOf(newer), "ja", zone)
        val withOlder = MessageGrouping.rowsFor(listOf(older, newer), "ja", zone)

        val keyAlone = MessageGrouping.keysFor(alone)[alone.indexOfFirst { it is ChatRow.Bubble }]
        val keyWithOlder = MessageGrouping.keysFor(withOlder)[withOlder.indexOfLast { it is ChatRow.Bubble }]

        assertEquals("ta sama wiadomość musi mieć ten sam klucz niezależnie od pozycji", keyAlone, keyWithOlder)
    }

    @Test
    fun `dwie wiadomosci z tej samej milisekundy maja rozne klucze wynikajace z ich identyfikatorow`() {
        val time = at(2026, 10, 7, 9)
        val first = MessageModel(messageId = "id-1", chatId = "c", senderId = "ja", text = "a", timestamp = time)
        val second = MessageModel(messageId = "id-2", chatId = "c", senderId = "on", text = "b", timestamp = time)

        val rows = MessageGrouping.rowsFor(listOf(first, second), "ja", zone)
        val keys = MessageGrouping.keysFor(rows)

        assertEquals(listOf("day:07.10.2026", "msg:id-1", "msg:id-2"), keys)
    }
}
