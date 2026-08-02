package com.example.ui.wallet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.WalletTransactionEntity
import com.example.data.repository.UserRepository
import com.example.data.repository.WalletRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val userRepository = UserRepository(database.userDao(), database.favoriteDao())
    private val walletRepository = WalletRepository(database.walletTransactionDao())

    val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val walletTransactions: StateFlow<List<WalletTransactionEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) {
                walletRepository.getWalletTransactionsFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun topUpWallet(amount: Int) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val transaction = WalletTransactionEntity(
                userId = user.id,
                amount = amount,
                title = "Doładowanie portfela"
            )
            walletRepository.insertWalletTransaction(transaction)
            userRepository.updateUser(user.copy(coinsBalance = user.coinsBalance + amount))
        }
    }

    fun spendCoins(amount: Int, title: String): Boolean {
        val user = currentUser.value ?: return false
        if (user.coinsBalance < amount) return false
        viewModelScope.launch {
            val transaction = WalletTransactionEntity(
                userId = user.id,
                amount = -amount,
                title = title
            )
            walletRepository.insertWalletTransaction(transaction)
            userRepository.updateUser(user.copy(coinsBalance = user.coinsBalance - amount))
        }
        return true
    }
}
