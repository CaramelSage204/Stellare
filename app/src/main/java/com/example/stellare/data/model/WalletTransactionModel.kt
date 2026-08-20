package com.example.stellare.data.model

data class WalletTransactionModel(
    val transactionId: String = "",
    val userId: String = "",
    val amount: Int = 0,
    val title: String = "",
    val timestamp: Long = 0L
)
