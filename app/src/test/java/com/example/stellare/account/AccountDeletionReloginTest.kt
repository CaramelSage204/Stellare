package com.example.stellare.account

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.AccountDeleter
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.ui.settings.AccountDeletionState
import com.example.stellare.ui.settings.AccountDeletionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDeletionReloginTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class Rig {
        val log = EventLog()
        val auth = AccountAuthDataSource(log, lastSignIn = AccountAuthDataSource.NOW - 3_600_000)
        val users = AccountUserDataSource(log, listOf(UserModel(id = "u1")))
        val privateProfiles = AccountPrivateProfileDataSource(log)
        val repository = UserRepository(auth, users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfiles)
        val deleter = AccountDeleter(
            auth, users, privateProfiles, AccountPostDataSource(log), AccountChatDataSource(), AccountNoteDataSource(log),
            AccountAppointmentDataSource(log), AccountVerificationDataSource(log)
        ) { AccountAuthDataSource.NOW }
        val viewModel = AccountDeletionViewModel(deleter, repository, auth)
    }

    @Test
    fun `ponowne logowanie zdejmuje karte swiezego logowania, zeby ekran znow pozwalal zaczac`() = runTest(dispatcher) {
        val rig = Rig()
        rig.viewModel.deleteAccount()
        advanceUntilIdle()
        assertEquals(AccountDeletionState.RecentLoginRequired, rig.viewModel.state.value)

        rig.viewModel.signOutToRelogin()
        advanceUntilIdle()

        assertEquals(
            "po wylogowaniu do ponownego logowania karta błędu nie może przeżyć i blokować pola zgody",
            AccountDeletionState.Idle,
            rig.viewModel.state.value
        )
    }
}
