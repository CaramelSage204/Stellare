package com.example.stellare.data.model

data class WalletTransactionModel(
    val transactionId: String = "",
    val userId: String = "",
    val counterpartyId: String? = null,
    val appointmentId: String? = null,
    val amount: Int = 0,
    val type: WalletTransactionType = WalletTransactionType.TOPUP,
    val title: String = "",
    val timestamp: Long = 0L
)
