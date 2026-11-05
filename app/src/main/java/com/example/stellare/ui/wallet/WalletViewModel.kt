package com.example.stellare.ui.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.NameLookup
import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.WalletRepository
import com.example.stellare.ui.StaleData
import com.example.stellare.ui.UncertainWriteMessages
import com.example.stellare.ui.UserFacingError
import com.example.stellare.ui.WriteOutcome
import com.example.stellare.ui.WriteReconciliation
import com.example.stellare.ui.retryWithBackoff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModel(
    private val walletRepository: WalletRepository,
    private val userRepository: UserRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newEntryKey: () -> String = { "topup_${UUID.randomUUID()}" }
) : ViewModel() {

    val balance: StateFlow<Int> = userRepository.currentPrivateProfileFlow()
        .retryWithBackoff("Wallet", "salda")
        .map { it?.coinsBalance ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val historyStale = StaleData(HISTORY_STALE_MESSAGE)
    val historyStaleMessage: StateFlow<String?> = historyStale.message

    fun retryHistory() {
        historyStale.retry()
    }

    val transactions: StateFlow<List<WalletTransactionModel>> = historyStale
        .cover("Wallet", "historii portfela") {
            userRepository.signedInUserIdFlow().flatMapLatest { uid ->
                if (uid != null) walletRepository.getWalletTransactionsFlow(uid) else flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val lookedUpNames = mutableMapOf<String, NameLookup>()
    private val nameRetryRequests = MutableStateFlow(0)

    fun retryNames() {
        nameRetryRequests.value++
    }

    val history: StateFlow<List<WalletEntry>> = combine(transactions, nameRetryRequests) { list, _ -> list }
        .transform { list ->
            val unsettled = list.mapNotNull { it.counterpartyId }.distinct()
                .filterNot { lookedUpNames[it]?.isSettled == true }
            emit(entriesOf(list, loading = unsettled.toSet()))
            if (unsettled.isNotEmpty()) {
                lookedUpNames.putAll(userRepository.lookupNames(unsettled.toSet()))
                emit(entriesOf(list, loading = emptySet()))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun entriesOf(list: List<WalletTransactionModel>, loading: Set<String>): List<WalletEntry> =
        list.map { tx ->
            val id = tx.counterpartyId
            val lookup = when {
                id == null -> NameLookup.Absent
                id in loading -> NameLookup.Loading
                else -> lookedUpNames[id] ?: NameLookup.Loading
            }
            WalletEntryMapper.map(tx, lookup)
        }

    private val _topUpError = MutableStateFlow<String?>(null)
    val topUpError: StateFlow<String?> = _topUpError.asStateFlow()

    private val _topUpInProgress = MutableStateFlow(false)
    val topUpInProgress: StateFlow<Boolean> = _topUpInProgress.asStateFlow()

    private val _topUpCompleted = MutableStateFlow<Int?>(null)
    val topUpCompleted: StateFlow<Int?> = _topUpCompleted.asStateFlow()

    fun clearTopUpError() {
        _topUpError.value = null
    }

    fun consumeTopUpCompleted() {
        _topUpCompleted.value = null
    }

    fun topUp(amount: Int) {
        if (_topUpInProgress.value) return
        _topUpInProgress.value = true
        _topUpError.value = null
        _topUpCompleted.value = null
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser()
                if (user == null) {
                    _topUpError.value = NOT_SIGNED_IN_MESSAGE
                } else if (amount !in TopUpLimits.RANGE) {
                    _topUpError.value = "Jednorazowo można doładować od ${TopUpLimits.MIN_COINS} do ${TopUpLimits.MAX_COINS} monet."
                } else {
                    reconcileTopUp(user.id, amount)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Wallet", "Nie udalo sie doladowac portfela", e)
                _topUpError.value = UserFacingError.message(e, TOP_UP_FAILED_MESSAGE)
            } finally {
                _topUpInProgress.value = false
            }
        }
    }

    private class PendingTopUp(val userId: String, val amount: Int, val key: String, val startedAt: Long) {
        var attempted = false
    }

    private var pendingTopUp: PendingTopUp? = null

    private fun pendingFor(userId: String, amount: Int): PendingTopUp {
        val now = clock()
        val reusable = pendingTopUp?.takeIf {
            it.userId == userId && it.amount == amount && now - it.startedAt in 0..PENDING_TOP_UP_TTL_MS
        }
        return reusable ?: PendingTopUp(userId, amount, newEntryKey(), now).also { pendingTopUp = it }
    }

    private suspend fun reconcileTopUp(userId: String, amount: Int) {
        val pending = pendingFor(userId, amount)
        val outcome = WriteReconciliation.attempt(
            write = {
                walletRepository.topUp(userId, amount, "Doładowanie portfela", clock(), pending.key)
                amount
            },
            landed = { amount.takeIf { walletRepository.topUpLanded(userId, pending.key) } },
            failureMessage = TOP_UP_FAILED_MESSAGE,
            uncertainMessage = UncertainWriteMessages.TOP_UP,
            verifyFirst = pending.attempted,
            onError = { Log.e("Wallet", "Nie udalo sie doladowac portfela", it) }
        )
        pending.attempted = true
        when (outcome) {
            is WriteOutcome.Succeeded -> {
                pendingTopUp = null
                _topUpCompleted.value = outcome.value
            }
            is WriteOutcome.Failed -> _topUpError.value = outcome.message
            is WriteOutcome.Unknown -> _topUpError.value = outcome.message
        }
    }

    private companion object {
        const val PENDING_TOP_UP_TTL_MS = 10 * 60 * 1000L
        const val NOT_SIGNED_IN_MESSAGE = "Brak zalogowanego użytkownika."
        const val HISTORY_STALE_MESSAGE =
            "Nie udało się odświeżyć historii portfela. Pokazane pozycje mogą być nieaktualne."
        const val TOP_UP_FAILED_MESSAGE = "Nie udało się doładować portfela. Saldo nie zostało zmienione. Spróbuj ponownie."
    }
}