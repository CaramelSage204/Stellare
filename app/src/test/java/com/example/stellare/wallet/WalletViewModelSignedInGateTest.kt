package com.example.stellare.wallet

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.WalletRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.ui.wallet.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelSignedInGateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun zbuduj(
        signedInAs: String?,
        users: FakeUserDataSource = FakeUserDataSource(listOf(UserModel(id = "k1")), balances = mapOf("k1" to 100))
    ): Triple<WalletViewModel, FakeWalletDataSource, FakeAuthDataSource> {
        val auth = FakeAuthDataSource(signedInAs)
        val userRepository = UserRepository(auth, users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users))
        val wallet = FakeWalletDataSource()
        return Triple(WalletViewModel(WalletRepository(wallet), userRepository), wallet, auth)
    }

    @Test
    fun `niezalogowany nie pyta o operacje portfela`() = runTest(dispatcher) {
        val (vm, wallet, _) = zbuduj(signedInAs = null)
        backgroundScope.launch { vm.transactions.collect {} }
        runCurrent()

        assertEquals(0, wallet.subscriptionCount)
    }

    @Test
    fun `zalogowany ma jeden aktywny nasluch operacji portfela`() = runTest(dispatcher) {
        val (vm, wallet, _) = zbuduj(signedInAs = "k1")
        backgroundScope.launch { vm.transactions.collect {} }
        runCurrent()

        assertEquals(1, wallet.activeSubscriptions)
    }

    @Test
    fun `wylogowanie zdejmuje nasluch operacji portfela od razu`() = runTest(dispatcher) {
        val (vm, wallet, auth) = zbuduj(signedInAs = "k1")
        backgroundScope.launch { vm.transactions.collect {} }
        runCurrent()

        auth.signOut()
        runCurrent()

        assertEquals(0, wallet.activeSubscriptions)
    }

    @Test
    fun `zalogowany bez dokumentu uzytkownika ma aktywny nasluch portfela`() = runTest(dispatcher) {
        val (vm, wallet, _) = zbuduj(signedInAs = "k1", users = FakeUserDataSource())
        backgroundScope.launch { vm.transactions.collect {} }
        runCurrent()

        assertEquals(1, wallet.activeSubscriptions)
    }

    @Test
    fun `operacje portfela pojawiaja sie po zapisaniu dokumentu bez ponownego logowania`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        val (vm, wallet, _) = zbuduj(signedInAs = "k1", users = users)
        backgroundScope.launch { vm.transactions.collect {} }
        runCurrent()

        users.saveUser(UserModel(id = "k1"))
        wallet.saveTransaction(WalletTransactionModel(transactionId = "t1", userId = "k1", amount = 50))
        runCurrent()

        assertEquals(listOf("t1"), vm.transactions.value.map { it.transactionId })
        assertEquals(1, wallet.subscriptionCount)
    }
}
