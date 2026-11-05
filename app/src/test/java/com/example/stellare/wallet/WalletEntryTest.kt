package com.example.stellare.wallet

import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.ui.wallet.WalletEntryMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletEntryTest {

    private fun tx(type: WalletTransactionType, amount: Int, counterparty: String? = null, title: String = "t") =
        WalletTransactionModel(transactionId = "x", userId = "u", counterpartyId = counterparty, amount = amount, type = type, title = title)

    @Test
    fun `doladowanie - etykieta, plus, brak drugiej strony`() {
        val e = WalletEntryMapper.map(tx(WalletTransactionType.TOPUP, 200), null)
        assertEquals("Doładowanie", e.label)
        assertEquals("+200", e.signedAmount)
        assertTrue(e.isIncome)
        assertNull(e.counterpartyLine)
    }

    @Test
    fun `depozyt za wizyte - etykieta, minus, Wizyta u psychologa a nie Do`() {
        val e = WalletEntryMapper.map(tx(WalletTransactionType.BOOKING_PAYMENT, -80, "p1"), "Anna Nowak")
        assertEquals("Depozyt za wizytę", e.label)
        assertEquals("-80", e.signedAmount)
        assertFalse(e.isIncome)
        assertEquals("Wizyta u: Anna Nowak", e.counterpartyLine)
    }

    @Test
    fun `wplyw za wizyte - etykieta, plus, Od pacjenta`() {
        val e = WalletEntryMapper.map(tx(WalletTransactionType.BOOKING_EARNING, 80, "k1"), "Jan Kowalski")
        assertEquals("Wpływ za wizytę", e.label)
        assertEquals("+80", e.signedAmount)
        assertEquals("Od: Jan Kowalski", e.counterpartyLine)
    }

    @Test
    fun `zwrot po stronie pacjenta - plus i Wizyta u, bo zwrot idzie z depozytu, a nie od psychologa`() {
        val e = WalletEntryMapper.map(tx(WalletTransactionType.REFUND, 80, "p1"), "Anna Nowak")
        assertEquals("Zwrot za wizytę", e.label)
        assertEquals("+80", e.signedAmount)
        assertEquals("księga nie może przypisywać zwrotu portfelowi psychologa, bo monety wracają z depozytu", "Wizyta u: Anna Nowak", e.counterpartyLine)
        assertFalse("ksiega nie moze przypisywac zwrotu portfelowi psychologa", e.counterpartyLine!!.startsWith("Od:"))
    }

    @Test
    fun `zwrot po stronie psychologa - minus i Do`() {
        val e = WalletEntryMapper.map(tx(WalletTransactionType.REFUND, -80, "k1"), "Jan Kowalski")
        assertEquals("Zwrot dla pacjenta", e.label)
        assertEquals("-80", e.signedAmount)
        assertEquals("Do: Jan Kowalski", e.counterpartyLine)
    }

    @Test
    fun `nieznana druga strona ma neutralna nazwe`() {
        val e = WalletEntryMapper.map(tx(WalletTransactionType.REFUND, 80, "p1"), null)
        assertEquals("Wizyta u: inny użytkownik", e.counterpartyLine)
    }

    @Test
    fun `przelew - etykieta Przelew, minus, Do`() {
        val e = WalletEntryMapper.map(tx(WalletTransactionType.TRANSFER_OUT, -30, "x", title = "Przelew do: ktos"), "Ktos")
        assertEquals("Przelew", e.label)
        assertEquals("-30", e.signedAmount)
        assertEquals("Do: Ktos", e.counterpartyLine)
    }

    @Test
    fun `wyplata - etykieta Wyplata srodkow, minus`() {
        val e = WalletEntryMapper.map(tx(WalletTransactionType.WITHDRAWAL, -30, title = "cokolwiek"), null)
        assertEquals("Wypłata środków", e.label)
        assertEquals("-30", e.signedAmount)
    }

    @Test
    fun `ujemne doladowanie nie zmienia etykiety na tytul`() {
        assertEquals("Doładowanie", WalletEntryMapper.map(tx(WalletTransactionType.TOPUP, -30, title = "Inny tytul"), null).label)
    }
}
