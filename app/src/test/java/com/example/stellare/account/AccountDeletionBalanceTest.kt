package com.example.stellare.account

import com.example.stellare.data.model.PrivateProfile
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.AccountDeleter
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakePrivateProfileDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.ui.settings.AccountDeletionViewModel
import com.example.stellare.ui.settings.BalanceState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDeletionBalanceTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(privateProfiles: FakePrivateProfileDataSource): AccountDeletionViewModel {
        val log = EventLog()
        val auth = AccountAuthDataSource(log)
        val users = AccountUserDataSource(log, listOf(UserModel(id = "u1")))
        val repository = UserRepository(auth, users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfiles)
        val deleter = AccountDeleter(
            auth, users, privateProfiles, AccountPostDataSource(log), AccountChatDataSource(), AccountNoteDataSource(log),
            AccountAppointmentDataSource(log), AccountVerificationDataSource(log)
        ) { AccountAuthDataSource.NOW }
        return AccountDeletionViewModel(deleter, repository, auth)
    }

    @Test
    fun `saldo na ekranie usuwania pochodzi z dokumentu prywatnego`() = runTest(dispatcher) {
        val vm = viewModel(FakePrivateProfileDataSource(mapOf("u1" to PrivateProfile(coinsBalance = 40))))
        backgroundScope.launch { vm.balance.collect {} }
        runCurrent()

        assertEquals(BalanceState.Known(40), vm.balance.value)
    }

    @Test
    fun `przed pierwszym odczytem stan to wczytywanie, a nie brak i nie zero`() = runTest(dispatcher) {
        val vm = viewModel(FakePrivateProfileDataSource(mapOf("u1" to PrivateProfile(coinsBalance = 40))))

        assertEquals(BalanceState.Loading, vm.balance.value)
    }

    @Test
    fun `trwala odmowa odczytu salda daje Unavailable i nie jest ponawiana w nieskonczonosc`() = runTest(dispatcher) {
        val privateProfiles = FakePrivateProfileDataSource(mapOf("u1" to PrivateProfile(coinsBalance = 40))).apply {
            readFailure = FirebaseFirestoreException("PERMISSION_DENIED", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
        val vm = viewModel(privateProfiles)
        backgroundScope.launch { vm.balance.collect {} }
        runCurrent()
        advanceTimeBy(5 * 60_000)
        runCurrent()

        assertEquals(BalanceState.Unavailable, vm.balance.value)
        assertEquals(
            "trwała odmowa reguł nie przejdzie po ponowieniu, a każda próba zużywa baterię i limity",
            1,
            privateProfiles.subscriptionCount
        )
    }

    @Test
    fun `brak dokumentu prywatnego daje saldo nieznane, a nie zero`() = runTest(dispatcher) {
        val vm = viewModel(FakePrivateProfileDataSource())
        backgroundScope.launch { vm.balance.collect {} }
        runCurrent()

        assertEquals("brak dokumentu to osobny stan, nie błąd odczytu i nie zero", BalanceState.Missing, vm.balance.value)
    }

    @Test
    fun `nieudany odczyt salda jest ponawiany i ekran dostaje saldo bez wychodzenia z niego`() = runTest(dispatcher) {
        val privateProfiles = FakePrivateProfileDataSource(mapOf("u1" to PrivateProfile(coinsBalance = 40))).apply {
            readFailuresBeforeSuccess = 1
        }
        val vm = viewModel(privateProfiles)
        backgroundScope.launch { vm.balance.collect {} }
        runCurrent()
        assertEquals("błąd odczytu to stan Unavailable, nie Loading i nie Missing", BalanceState.Unavailable, vm.balance.value)

        advanceTimeBy(1_500)
        runCurrent()

        assertEquals("raz zbuforowana porażka odczytu zostałaby na ekranie na zawsze", BalanceState.Known(40), vm.balance.value)
        assertEquals(2, privateProfiles.subscriptionCount)
    }
}
