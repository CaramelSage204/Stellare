package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallet_transactions")
data class WalletTransactionEntity(
    @PrimaryKey(autoGenerate = true) val transactionId: Int = 0,
    @ColumnInfo(name = "userId") val userId: Int,
    @ColumnInfo(name = "amount") val amount: Int, // e.g. +50, -30
    @ColumnInfo(name = "title") val title: String, // e.g., "Doładowanie", "Opłata za konsultację"
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)
