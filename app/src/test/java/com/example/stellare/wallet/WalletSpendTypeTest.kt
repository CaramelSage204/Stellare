package com.example.stellare.wallet

import com.example.stellare.data.repository.WalletRepository
import com.example.stellare.fakes.FakeUserDataSource
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletSpendTypeTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(): WalletViewModel {
        val users = FakeUserDataSource()
        return WalletViewModel(WalletRepository(FakeWalletDataSource(users)), fakeUserRepository("k1", 200, users))
    }

    @Test
    fun `doladowanie ma niezerowy czas bliski teraz`() = runTest(dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.transactions.collect {} }
        advanceUntilIdle()
        val przed = System.currentTimeMillis()
        vm.topUp(100)
        advanceUntilIdle()
        val timestamp = vm.transactions.value.single().timestamp
        assertNotEquals(0L, timestamp)
        assertTrue(timestamp >= przed)
    }

}
