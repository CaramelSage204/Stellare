package com.example.stellare.data.remote

import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.model.WalletTransactionType

internal object WalletLedgerFields {
    const val USER_ID = "userId"
    const val TRANSACTION_ID = "transactionId"
}

internal data class TopUpPlan(val entry: WalletTransactionModel, val balanceIncrement: Long)

internal fun planTopUp(userId: String, amount: Int, title: String, timestamp: Long, entryId: String): TopUpPlan {
    require(amount > 0) { "Doładowanie wymaga kwoty dodatniej" }
    require(entryId.isNotBlank()) { "Doładowanie wymaga klucza idempotencji; losowy identyfikator uniemożliwia uzgodnienie po błędzie" }
    return TopUpPlan(
        entry = WalletTransactionModel(
            transactionId = entryId,
            userId = userId,
            amount = amount,
            type = WalletTransactionType.TOPUP,
            title = title,
            timestamp = timestamp
        ),
        balanceIncrement = amount.toLong()
    )
}
