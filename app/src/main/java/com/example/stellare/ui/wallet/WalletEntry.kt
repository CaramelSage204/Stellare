package com.example.stellare.ui.wallet

import com.example.stellare.data.model.NameLookup
import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.model.WalletTransactionType

data class WalletEntry(
    val transactionId: String,
    val label: String,
    val signedAmount: String,
    val isIncome: Boolean,
    val counterpartyLine: String?,
    val timestamp: Long,
    val nameUnavailable: Boolean = false
)

object WalletEntryMapper {

    fun map(tx: WalletTransactionModel, counterpartyName: String?): WalletEntry =
        map(tx, counterpartyName?.let(NameLookup::Known) ?: NameLookup.Absent)

    fun map(tx: WalletTransactionModel, counterparty: NameLookup): WalletEntry {
        val income = tx.amount > 0
        return WalletEntry(
            transactionId = tx.transactionId,
            label = labelOf(tx),
            signedAmount = if (income) "+${tx.amount}" else "${tx.amount}",
            isIncome = income,
            counterpartyLine = tx.counterpartyId?.let {
                counterpartyPrefix(tx, income) + nameText(counterparty)
            },
            timestamp = tx.timestamp,
            nameUnavailable = tx.counterpartyId != null && counterparty == NameLookup.Unavailable
        )
    }

    private fun counterpartyPrefix(tx: WalletTransactionModel, income: Boolean): String = when {
        tx.type == WalletTransactionType.BOOKING_PAYMENT -> "Wizyta u: "
        tx.type == WalletTransactionType.REFUND && income -> "Wizyta u: "
        income -> "Od: "
        else -> "Do: "
    }

    private fun nameText(counterparty: NameLookup): String = when (counterparty) {
        NameLookup.Loading -> "wczytywanie…"
        is NameLookup.Known -> counterparty.name
        NameLookup.Absent -> "inny użytkownik"
        NameLookup.Unavailable -> "nie udało się wczytać nazwy"
    }

    private fun labelOf(tx: WalletTransactionModel): String = when (tx.type) {
        WalletTransactionType.TOPUP -> "Doładowanie"
        WalletTransactionType.BOOKING_PAYMENT -> "Depozyt za wizytę"
        WalletTransactionType.BOOKING_EARNING -> "Wpływ za wizytę"
        WalletTransactionType.REFUND -> if (tx.amount > 0) "Zwrot za wizytę" else "Zwrot dla pacjenta"
        WalletTransactionType.TRANSFER_OUT -> "Przelew"
        WalletTransactionType.WITHDRAWAL -> "Wypłata środków"
    }
}
