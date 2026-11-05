package com.example.stellare.wallet

import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.data.repository.WalletRepository
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.fakes.fakeUserRepository
import com.example.stellare.ui.wallet.CoinPacks
import com.example.stellare.ui.wallet.TopUpLimits
import com.example.stellare.ui.wallet.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun walletViewModelZSaldem(saldo: Int): WalletViewModel {
        val users = FakeUserDataSource()
        val userRepository = fakeUserRepository("k1", saldo, users)
        val walletRepository = WalletRepository(FakeWalletDataSource(users))
        return WalletViewModel(walletRepository, userRepository)
    }

    @Test
    fun `doladowanie podnosi saldo i zapisuje operacje`() = runTest(dispatcher) {
        val vm = walletViewModelZSaldem(100)
        backgroundScope.launch { vm.balance.collect {} }
        backgroundScope.launch { vm.transactions.collect {} }
        advanceUntilIdle()

        vm.topUp(200)
        advanceUntilIdle()

        assertEquals(300, vm.balance.value)
        assertEquals(1, vm.transactions.value.size)
        assertEquals(WalletTransactionType.TOPUP, vm.transactions.value.first().type)
    }

    @Test
    fun `doladowanie ponad limit jest odrzucane`() = runTest(dispatcher) {
        val vm = walletViewModelZSaldem(100)
        backgroundScope.launch { vm.balance.collect {} }
        advanceUntilIdle()

        vm.topUp(5000)
        advanceUntilIdle()

        assertEquals(100, vm.balance.value)
        assertNotNull(vm.topUpError.value)
    }

    @Test
    fun `doladowanie kwota niedodatnia jest odrzucane`() = runTest(dispatcher) {
        val vm = walletViewModelZSaldem(100)
        backgroundScope.launch { vm.balance.collect {} }
        advanceUntilIdle()

        vm.topUp(0)
        advanceUntilIdle()

        assertEquals(100, vm.balance.value)
        assertNotNull(vm.topUpError.value)
    }

    @Test
    fun `kazdy pakiet oferowany w aplikacji jest przyjmowany przez doladowanie`() {
        CoinPacks.forEach { pack ->
            runTest(dispatcher) {
                val vm = walletViewModelZSaldem(100)
                backgroundScope.launch { vm.balance.collect {} }
                advanceUntilIdle()

                vm.topUp(pack.coins)
                advanceUntilIdle()

                assertNull(
                    "aplikacja oferuje pakiet \"${pack.name}\" (${pack.coins} monet), a doładowanie go odrzuca: ${vm.topUpError.value}",
                    vm.topUpError.value
                )
                assertEquals(100 + pack.coins, vm.balance.value)
            }
        }
    }

    @Test
    fun `doladowanie dokladnie w granicy limitu jest przyjmowane`() = runTest(dispatcher) {
        val vm = walletViewModelZSaldem(100)
        backgroundScope.launch { vm.balance.collect {} }
        advanceUntilIdle()

        vm.topUp(TopUpLimits.MAX_COINS)
        advanceUntilIdle()

        assertNull(vm.topUpError.value)
        assertEquals(100 + TopUpLimits.MAX_COINS, vm.balance.value)
    }
}
