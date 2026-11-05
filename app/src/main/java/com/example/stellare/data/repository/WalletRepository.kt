package com.example.stellare.data.repository

import com.example.stellare.data.remote.WalletDataSource
import com.example.stellare.data.model.WalletTransactionModel
import kotlinx.coroutines.flow.Flow

class WalletRepository(private val walletDataSource: WalletDataSource) {

    fun getWalletTransactionsFlow(userId: String): Flow<List<WalletTransactionModel>> =
        walletDataSource.getTransactionsForUser(userId)

    suspend fun topUp(userId: String, amount: Int, title: String, timestamp: Long, entryId: String) =
        walletDataSource.topUp(userId, amount, title, timestamp, entryId)

    suspend fun topUpLanded(userId: String, entryId: String): Boolean =
        walletDataSource.topUpLanded(userId, entryId)
}