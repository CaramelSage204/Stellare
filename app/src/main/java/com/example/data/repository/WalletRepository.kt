package com.example.data.repository

import com.example.data.local.dao.WalletTransactionDao
import com.example.data.local.entity.WalletTransactionEntity
import kotlinx.coroutines.flow.Flow

class WalletRepository(private val walletTransactionDao: WalletTransactionDao) {
    fun getWalletTransactionsFlow(userId: Int): Flow<List<WalletTransactionEntity>> = walletTransactionDao.getTransactionsForUserFlow(userId)
    suspend fun insertWalletTransaction(transaction: WalletTransactionEntity): Long = walletTransactionDao.insertTransaction(transaction)
}
