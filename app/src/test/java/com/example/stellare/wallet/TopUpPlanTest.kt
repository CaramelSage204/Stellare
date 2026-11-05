package com.example.stellare.wallet

import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.data.remote.WalletLedgerFields
import com.example.stellare.data.remote.planTopUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class TopUpPlanTest {

    @Test
    fun `wpis ksiegi dostaje dokladnie przekazany klucz, a nie losowy identyfikator`() {
        val plan = planTopUp("k1", 50, "Doladowanie portfela", 123L, "topup_abc")

        assertEquals("topup_abc", plan.entry.transactionId)
    }

    @Test
    fun `plan niesie przyrost salda rowny kwocie i wpis TOPUP uzytkownika`() {
        val plan = planTopUp("k1", 50, "Doladowanie portfela", 123L, "topup_abc")

        assertEquals(50L, plan.balanceIncrement)
        assertEquals(
            WalletTransactionModel(
                transactionId = "topup_abc", userId = "k1", amount = 50,
                type = WalletTransactionType.TOPUP, title = "Doladowanie portfela", timestamp = 123L
            ),
            plan.entry
        )
    }

    @Test
    fun `dwa plany z roznych kluczy to dwa rozne wpisy, a z tego samego klucza ten sam wpis`() {
        val a = planTopUp("k1", 50, "t", 1L, "klucz-a").entry.transactionId
        val b = planTopUp("k1", 50, "t", 2L, "klucz-b").entry.transactionId
        val aAgain = planTopUp("k1", 50, "t", 3L, "klucz-a").entry.transactionId

        assertEquals(false, a == b)
        assertEquals(a, aAgain)
    }

    @Test
    fun `pusty klucz jest odrzucany, bo bez klucza uzgodnienie jest niewykonalne`() {
        listOf("", "   ").forEach { key ->
            try {
                planTopUp("k1", 50, "t", 1L, key)
                fail("klucz '$key' musi być odrzucony")
            } catch (expected: IllegalArgumentException) {
                assertNotNull(expected.message)
            }
        }
    }

    @Test
    fun `kwota niedodatnia jest odrzucana`() {
        listOf(0, -5).forEach { amount ->
            try {
                planTopUp("k1", amount, "t", 1L, "klucz")
                fail("kwota $amount musi być odrzucona")
            } catch (expected: IllegalArgumentException) {
                assertNotNull(expected.message)
            }
        }
    }

    @Test
    fun `nazwy pol zapytania sprawdzajacego sa polami modelu zapisywanego do bazy`() {
        val fields = WalletTransactionModel::class.java.declaredFields.map { it.name }

        assertEquals(true, WalletLedgerFields.USER_ID in fields)
        assertEquals(true, WalletLedgerFields.TRANSACTION_ID in fields)
    }
}
