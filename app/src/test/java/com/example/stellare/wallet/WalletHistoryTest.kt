package com.example.stellare.wallet

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.WalletTransactionModel
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletHistoryTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `historia podstawia imie i nazwisko drugiej strony`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(UserModel(id = "p1", firstName = "Anna", lastName = "Nowak")))
        val wallet = FakeWalletDataSource()
        wallet.transactions.value = listOf(
            WalletTransactionModel(
                transactionId = "a", userId = "k1", counterpartyId = "p1", amount = 80,
                type = WalletTransactionType.REFUND, title = "Zwrot"
            )
        )
        val vm = WalletViewModel(WalletRepository(wallet), fakeUserRepository("k1", userDataSource = users))
        backgroundScope.launch { vm.history.collect {} }
        advanceUntilIdle()

        val entry = vm.history.value.single()
        assertEquals("Zwrot za wizytę", entry.label)
        assertEquals("+80", entry.signedAmount)
        assertEquals("Wizyta u: Anna Nowak", entry.counterpartyLine)
    }
}
