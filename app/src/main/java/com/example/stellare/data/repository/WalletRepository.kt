package com.example.stellare.data.repository

import com.example.stellare.data.remote.FirestoreWalletTransactionDataSource
import com.example.stellare.data.model.WalletTransactionModel
import kotlinx.coroutines.flow.Flow

class WalletRepository(private val walletDataSource: FirestoreWalletTransactionDataSource) {

    fun getWalletTransactionsFlow(userId: String): Flow<List<WalletTransactionModel>> =
        walletDataSource.getTransactionsForUser(userId)

    suspend fun insertWalletTransaction(transaction: WalletTransactionModel) =
        walletDataSource.saveTransaction(transaction)
}