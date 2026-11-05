package com.example.stellare.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.AccountDeletionResult
import com.example.stellare.data.model.PrivateProfile
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.repository.AccountDeleter
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.ui.lastKnownUser
import com.example.stellare.ui.retryWithBackoff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AccountDeletionViewModel(
    private val deleter: AccountDeleter,
    private val userRepository: UserRepository,
    private val authDataSource: AuthDataSource
) : ViewModel() {

    val profile: StateFlow<UserModel?> = userRepository.getCurrentUserFlow()
        .lastKnownUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val balance: StateFlow<BalanceState> = userRepository.currentPrivateProfileFlow()
        .map<PrivateProfile?, BalanceState> { profile -> profile?.let { BalanceState.Known(it.coinsBalance) } ?: BalanceState.Missing }
        .retryWithBackoff("AccountDeletion", "salda") { emit(BalanceState.Unavailable) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BalanceState.Loading)

    private val _state = MutableStateFlow<AccountDeletionState>(AccountDeletionState.Idle)
    val state: StateFlow<AccountDeletionState> = _state.asStateFlow()

    fun deleteAccount() {
        if (_state.value == AccountDeletionState.Deleting) return
        _state.value = AccountDeletionState.Deleting
        userRepository.holdProfileObservation()
        viewModelScope.launch {
            try {
                when (val result = deleter.delete()) {
                    AccountDeletionResult.Deleted -> {
                        withTimeoutOrNull(SIGN_OUT_WAIT_MS) { userRepository.signedInUserIdFlow().first { it == null } }
                        _state.value = AccountDeletionState.Idle
                        userRepository.releaseProfileObservation()
                    }
                    AccountDeletionResult.RecentLoginRequired -> {
                        _state.value = AccountDeletionState.RecentLoginRequired
                        userRepository.releaseProfileObservation()
                    }
                    is AccountDeletionResult.Failed -> {
                        val profileGone = result.progress.userDocumentDeleted
                        _state.value = AccountDeletionState.Failed(
                            message = AccountDeletionMessages.failure(result),
                            profileGone = profileGone
                        )
                        if (!profileGone) userRepository.releaseProfileObservation()
                    }
                }
            } catch (e: CancellationException) {
                userRepository.releaseProfileObservation()
                throw e
            }
        }
    }

    fun signOutToRelogin() {
        _state.value = AccountDeletionState.Idle
        authDataSource.signOut()
    }

    fun dismissOutcome() {
        if (_state.value == AccountDeletionState.Deleting) return
        _state.value = AccountDeletionState.Idle
        userRepository.releaseProfileObservation()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
        const val SIGN_OUT_WAIT_MS = 5000L
    }
}
