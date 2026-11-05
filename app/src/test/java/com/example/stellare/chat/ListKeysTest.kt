package com.example.stellare.chat

import com.example.stellare.ui.ListKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class ListKeysTest {

    @Test
    fun `unikalne identyfikatory zostaja bez zmian`() {
        assertEquals(listOf("a", "b", "c"), ListKeys.unique(listOf("a", "b", "c")))
    }

    @Test
    fun `powtorzony identyfikator dostaje kolejne przyrostki`() {
        assertEquals(listOf("a", "a#1", "a#2"), ListKeys.unique(listOf("a", "a", "a")))
    }

    @Test
    fun `przyrostek nie moze zderzyc sie z prawdziwym identyfikatorem`() {
        val keys = ListKeys.unique(listOf("a", "a#1", "a"))

        assertEquals("zduplikowane klucze: $keys", 3, keys.toSet().size)
        assertEquals("a", keys[0])
        assertEquals("a#1", keys[1])
    }

    @Test
    fun `pusty identyfikator tez daje unikalne klucze`() {
        val keys = ListKeys.unique(listOf("", "", ""))

        assertEquals("zduplikowane klucze: $keys", 3, keys.toSet().size)
    }

    @Test
    fun `pusta lista daje pusty wynik`() {
        assertEquals(emptyList<String>(), ListKeys.unique(emptyList()))
    }
}
