package com.example.stellare.wallet

import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.data.repository.WalletRepository
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.fakes.fakeUserRepository
import com.example.stellare.ui.wallet.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletTopUpAtomicityTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val users = FakeUserDataSource()
    private val wallet = FakeWalletDataSource(users)
    private val vm by lazy { WalletViewModel(WalletRepository(wallet), fakeUserRepository("k1", 100, users)) }

    private fun TestScope.observe() {
        backgroundScope.launch { vm.balance.collect {} }
        backgroundScope.launch { vm.transactions.collect {} }
    }

    @Test
    fun `udane doladowanie zmienia saldo i ksiege razem i zglasza ukonczenie`() = runTest(dispatcher) {
        observe()
        advanceUntilIdle()
        vm.topUp(50)
        advanceUntilIdle()
        assertEquals(150, vm.balance.value)
        val wpis = vm.transactions.value.single()
        assertEquals(50, wpis.amount)
        assertEquals(WalletTransactionType.TOPUP, wpis.type)
        assertEquals(50, vm.topUpCompleted.value)
        assertNull(vm.topUpError.value)
        assertFalse(vm.topUpInProgress.value)
    }

    @Test
    fun `niepowodzenie doladowania nie zmienia ani salda ani ksiegi i pokazuje komunikat`() = runTest(dispatcher) {
        observe()
        advanceUntilIdle()
        wallet.topUpFailure = IllegalStateException("PERMISSION_DENIED: Missing or insufficient permissions.")
        vm.topUp(50)
        advanceUntilIdle()
        assertEquals(100, vm.balance.value)
        assertTrue(vm.transactions.value.isEmpty())
        assertEquals(
            "Nie udało się doładować portfela. Saldo nie zostało zmienione. Spróbuj ponownie.",
            vm.topUpError.value
        )
        assertNull(vm.topUpCompleted.value)
        assertFalse(vm.topUpInProgress.value)
    }

    @Test
    fun `po niepowodzeniu ponowne doladowanie dziala`() = runTest(dispatcher) {
        observe()
        advanceUntilIdle()
        wallet.topUpFailure = IllegalStateException("UNAVAILABLE")
        vm.topUp(50)
        advanceUntilIdle()
        wallet.topUpFailure = null
        vm.topUp(50)
        advanceUntilIdle()
        assertEquals(150, vm.balance.value)
        assertEquals(1, vm.transactions.value.size)
        assertNull(vm.topUpError.value)
    }

    @Test
    fun `odrzucona kwota nie dotyka ani salda ani ksiegi`() = runTest(dispatcher) {
        observe()
        advanceUntilIdle()
        vm.topUp(1001)
        advanceUntilIdle()
        assertEquals(100, vm.balance.value)
        assertTrue(vm.transactions.value.isEmpty())
        assertEquals("Jednorazowo można doładować od 1 do 1000 monet.", vm.topUpError.value)
    }
}
