package com.example.stellare.ui.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.WalletRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModel(
    private val walletRepository: WalletRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val balance: StateFlow<Int> = currentUser
        .map { it?.coinsBalance ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val transactions: StateFlow<List<WalletTransactionModel>> = currentUser
        .flatMapLatest { user ->
            if (user != null) {
                walletRepository.getWalletTransactionsFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _topUpError = MutableStateFlow<String?>(null)
    val topUpError: StateFlow<String?> = _topUpError.asStateFlow()

    fun clearTopUpError() {
        _topUpError.value = null
    }

    fun topUp(amount: Int) {
        viewModelScope.launch {
            _topUpError.value = null
            val user = userRepository.getCurrentUser() ?: return@launch
            if (amount !in 1..1000) {
                _topUpError.value = "Jednorazowo można doładować od 1 do 1000 monet."
                return@launch
            }
            try {
                val transaction = WalletTransactionModel(
                    userId = user.id,
                    amount = amount,
                    title = "Doładowanie portfela"
                )
                walletRepository.insertWalletTransaction(transaction)
                userRepository.updateUser(user.copy(coinsBalance = user.coinsBalance + amount))
            } catch (e: Exception) {
                Log.e("Wallet", "Nie udalo sie doladowac portfela", e)
            }
        }
    }

    suspend fun spend(amount: Int, title: String): Boolean {
        val user = userRepository.getCurrentUser() ?: return false
        if (user.coinsBalance < amount) return false
        return try {
            val transaction = WalletTransactionModel(
                userId = user.id,
                amount = -amount,
                title = title
            )
            walletRepository.insertWalletTransaction(transaction)
            userRepository.updateUser(user.copy(coinsBalance = user.coinsBalance - amount))
            true
        } catch (e: Exception) {
            Log.e("Wallet", "Nie udalo sie wydac monet", e)
            false
        }
    }
}
