package com.example.stellare.auth

import com.example.stellare.ui.auth.PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberTest {

    private fun assertNormalizes(expected: String, input: String) {
        assertEquals("numer '$input' ma byc wyslany jako $expected", expected, PhoneNumber.normalize(input))
    }

    private fun assertRejected(input: String) {
        assertNull("'$input' nie jest numerem telefonu i nie moze trafic do Firebase", PhoneNumber.normalize(input))
    }

    @Test
    fun `numer z prefiksem i spacjami dostaje format miedzynarodowy`() {
        assertNormalizes("+48501234567", "+48 501 234 567")
    }

    @Test
    fun `numer krajowy dziewieciocyfrowy dostaje prefiks polski`() {
        assertNormalizes("+48501234567", "501234567")
        assertNormalizes("+48501234567", "501 234 567")
    }

    @Test
    fun `myslniki i nawiasy sa pomijane`() {
        assertNormalizes("+48501234567", "501-234-567")
        assertNormalizes("+48501234567", "(501) 234 567")
    }

    @Test
    fun `podwojne zero zamiast plusa jest zamieniane na plus`() {
        assertNormalizes("+48501234567", "0048501234567")
    }

    @Test
    fun `numer zagraniczny jest przyjmowany`() {
        assertNormalizes("+447911123456", "+44 7911 123456")
    }

    @Test
    fun `numer z prefiksem polskim ma dokladnie dziewiec cyfr`() {
        assertRejected("+4850123456")
        assertRejected("+485012345678")
    }

    @Test
    fun `za krotki lub za dlugi numer jest odrzucany`() {
        assertRejected("12345")
        assertRejected("+1234567")
        assertRejected("+1234567890123456")
    }

    @Test
    fun `puste pole i same biale znaki sa odrzucane`() {
        assertRejected("")
        assertRejected("    ")
        assertRejected("+")
    }

    @Test
    fun `litery w numerze sa odrzucane`() {
        assertRejected("abc")
        assertRejected("+48 50x 234 567")
    }

    @Test
    fun `cyfry spoza ASCII sa odrzucane`() {
        assertRejected("５０１２３４５６７")
    }

    @Test
    fun `plus w srodku numeru jest odrzucany`() {
        assertRejected("48+501234567")
    }

    @Test
    fun `normalizacja jest idempotentna`() {
        val raz = PhoneNumber.normalize("501 234 567")!!
        assertEquals(raz, PhoneNumber.normalize(raz))
    }
}
