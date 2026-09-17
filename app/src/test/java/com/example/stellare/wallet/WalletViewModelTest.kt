package com.example.stellare.wallet

import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.data.repository.WalletRepository
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.fakes.fakeUserRepository
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun walletViewModelZSaldem(saldo: Int): WalletViewModel {
        val userRepository = fakeUserRepository("k1", saldo)
        val walletRepository = WalletRepository(FakeWalletDataSource())
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
    fun `spend przy niewystarczajacym saldzie zwraca false i nic nie zapisuje`() = runTest(dispatcher) {
        val vm = walletViewModelZSaldem(100)
        backgroundScope.launch { vm.balance.collect {} }
        backgroundScope.launch { vm.transactions.collect {} }
        advanceUntilIdle()

        val result = vm.spend(500, "Przelew do: odbiorca")
        advanceUntilIdle()

        assertEquals(false, result)
        assertEquals(100, vm.balance.value)
        assertEquals(0, vm.transactions.value.size)
    }

    @Test
    fun `spend przy wystarczajacym saldzie zwraca true, obniza saldo i zapisuje operacje ujemna`() = runTest(dispatcher) {
        val vm = walletViewModelZSaldem(200)
        backgroundScope.launch { vm.balance.collect {} }
        backgroundScope.launch { vm.transactions.collect {} }
        advanceUntilIdle()

        val result = vm.spend(80, "Przelew do: odbiorca")
        advanceUntilIdle()

        assertEquals(true, result)
        assertEquals(120, vm.balance.value)
        assertEquals(1, vm.transactions.value.size)
        assertEquals(-80, vm.transactions.value.first().amount)
    }
}
