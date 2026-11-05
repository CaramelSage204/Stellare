package com.example.stellare.reconciliation

import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.data.repository.WalletRepository
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.fakes.fakeUserRepository
import com.example.stellare.ui.UncertainWriteMessages
import com.example.stellare.ui.UserFacingError
import com.example.stellare.ui.wallet.WalletViewModel
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletReconciliationTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun unavailable() = FirebaseFirestoreException("brak sieci", Code.UNAVAILABLE)
    private fun denied() = FirebaseFirestoreException("odmowa regul", Code.PERMISSION_DENIED)

    private class Setup(startBalance: Int = 100) {
        val users = FakeUserDataSource()
        val wallet = FakeWalletDataSource(users)
        val repository = fakeUserRepository("k1", startBalance, users)
        var now = 1_000_000L
        val keys = mutableListOf<String>()
        val vm = WalletViewModel(WalletRepository(wallet), repository, { now }, { "klucz-${keys.size + 1}".also { keys += it } })

        fun balance() = checkNotNull(users.balanceOf("k1"))
        fun entries(key: String) = wallet.transactions.value.count { it.transactionId == key }
        fun topUps() = wallet.transactions.value.filter { it.type == WalletTransactionType.TOPUP }
    }

    private fun Setup.tapAndSettle(amount: Int, scope: kotlinx.coroutines.test.TestScope) {
        vm.topUp(amount)
        scope.advanceUntilIdle()
    }

    @Test
    fun `doladowanie doszlo, odpowiedz nie wrocila - uzytkownik widzi sukces, a saldo wzroslo raz`() = runTest(dispatcher) {
        val s = Setup()
        s.wallet.topUpResponseLost = unavailable()

        s.tapAndSettle(50, this)

        assertEquals("zapis musiał dojść do bazy, inaczej test nie bada utraty odpowiedzi", 1, s.entries("klucz-1"))
        assertEquals(50, s.vm.topUpCompleted.value)
        assertNull(s.vm.topUpError.value)
        assertEquals(150, s.balance())
        assertEquals(1, s.topUps().size)
    }

    @Test
    fun `doladowanie, ktore nie doszlo - porazka z komunikatem, saldo i ksiega bez zmian`() = runTest(dispatcher) {
        val s = Setup()
        s.wallet.topUpFailure = unavailable()

        s.tapAndSettle(50, this)

        assertEquals(UserFacingError.NO_CONNECTION, s.vm.topUpError.value)
        assertNull(s.vm.topUpCompleted.value)
        assertEquals(100, s.balance())
        assertTrue(s.wallet.transactions.value.isEmpty())
        assertEquals("sprawdzenie musiało się odbyć", 1, s.wallet.topUpLandedCalls)
    }

    @Test
    fun `doladowanie doszlo, a sprawdzenie zawiodlo - niewiedza, nie porazka`() = runTest(dispatcher) {
        val s = Setup()
        s.wallet.topUpResponseLost = unavailable()
        s.wallet.topUpLandedFailure = unavailable()

        s.tapAndSettle(50, this)

        assertEquals(UncertainWriteMessages.TOP_UP, s.vm.topUpError.value)
        assertNull(s.vm.topUpCompleted.value)
        assertEquals("doładowanie naprawdę doszło", 150, s.balance())
        assertFalse(s.vm.topUpError.value!!.contains("Nie udało się"))
    }

    @Test
    fun `odmowa regul przy doladowaniu nie uruchamia sprawdzenia`() = runTest(dispatcher) {
        val s = Setup()
        s.wallet.topUpFailure = denied()
        s.wallet.topUpLandedFailure = unavailable()

        s.tapAndSettle(50, this)

        assertEquals(
            "Nie udało się doładować portfela. Saldo nie zostało zmienione. Spróbuj ponownie.",
            s.vm.topUpError.value
        )
        assertEquals(0, s.wallet.topUpLandedCalls)
    }

    @Test
    fun `po niewiadomej ponowienie sprawdza przed zapisem i nie doladowuje drugi raz`() = runTest(dispatcher) {
        val s = Setup()
        s.wallet.topUpResponseLost = unavailable()
        s.wallet.topUpLandedFailure = unavailable()
        s.tapAndSettle(50, this)
        s.wallet.topUpLandedFailure = null

        s.tapAndSettle(50, this)

        assertEquals(50, s.vm.topUpCompleted.value)
        assertNull(s.vm.topUpError.value)
        assertEquals("druga próba nie może zapisać niczego nowego", 1, s.wallet.topUpWrites)
        assertEquals(1, s.entries("klucz-1"))
        assertEquals(1, s.topUps().size)
        assertEquals(150, s.balance())
        assertEquals(listOf("klucz-1"), s.keys)
    }

    @Test
    fun `ponowienie po niewiadomej, gdy zapis jednak nie doszedl, pisze pod tym samym kluczem raz`() = runTest(dispatcher) {
        val s = Setup()
        s.wallet.topUpFailure = unavailable()
        s.wallet.topUpLandedFailure = unavailable()
        s.tapAndSettle(50, this)
        assertEquals(UncertainWriteMessages.TOP_UP, s.vm.topUpError.value)
        s.wallet.topUpFailure = null
        s.wallet.topUpLandedFailure = null

        s.tapAndSettle(50, this)

        assertEquals(50, s.vm.topUpCompleted.value)
        assertEquals(1, s.entries("klucz-1"))
        assertEquals(150, s.balance())
        assertEquals(listOf("klucz-1"), s.keys)
    }

    @Test
    fun `ponowienie po prawdziwej porazce uzywa tego samego klucza i daje jeden wpis`() = runTest(dispatcher) {
        val s = Setup()
        s.wallet.topUpFailure = unavailable()
        s.tapAndSettle(50, this)
        s.wallet.topUpFailure = null

        s.tapAndSettle(50, this)

        assertEquals(50, s.vm.topUpCompleted.value)
        assertEquals(listOf("klucz-1"), s.keys)
        assertEquals(1, s.entries("klucz-1"))
        assertEquals(150, s.balance())
    }

    @Test
    fun `zapis spoznionej odpowiedzi pod tym samym kluczem jest odrzucony przez ksiege, wiec monety nie podwajaja sie`() = runTest(dispatcher) {
        val s = Setup()
        s.wallet.topUpResponseLost = unavailable()
        s.tapAndSettle(50, this)
        val balanceAfterFirst = s.balance()

        val error = runCatching { s.wallet.topUp("k1", 50, "Doładowanie portfela", 1L, "klucz-1") }.exceptionOrNull()

        assertTrue("księga jest niezmienna, więc drugi zapis tego samego klucza musi być odrzucony", error != null)
        assertEquals(balanceAfterFirst, s.balance())
        assertEquals(1, s.entries("klucz-1"))
    }

    @Test
    fun `po udanym doladowaniu kolejne dostaje nowy klucz`() = runTest(dispatcher) {
        val s = Setup()

        s.tapAndSettle(50, this)
        s.tapAndSettle(50, this)

        assertEquals(listOf("klucz-1", "klucz-2"), s.keys)
        assertEquals(2, s.topUps().size)
        assertEquals(200, s.balance())
        assertEquals(setOf("klucz-1", "klucz-2"), s.topUps().map { it.transactionId }.toSet())
    }

    @Test
    fun `inna kwota po niewiadomej dostaje nowy klucz`() = runTest(dispatcher) {
        val s = Setup()
        s.wallet.topUpResponseLost = unavailable()
        s.wallet.topUpLandedFailure = unavailable()
        s.tapAndSettle(50, this)
        s.wallet.topUpLandedFailure = null

        s.tapAndSettle(70, this)

        assertEquals(listOf("klucz-1", "klucz-2"), s.keys)
        assertEquals(70, s.vm.topUpCompleted.value)
    }

    @Test
    fun `klucz niewiadomej wygasa po dziesieciu minutach`() = runTest(dispatcher) {
        val s = Setup()
        s.wallet.topUpResponseLost = unavailable()
        s.wallet.topUpLandedFailure = unavailable()
        s.tapAndSettle(50, this)
        s.wallet.topUpLandedFailure = null
        s.now += 10 * 60 * 1000L + 1

        s.tapAndSettle(50, this)

        assertEquals(listOf("klucz-1", "klucz-2"), s.keys)
        assertEquals(2, s.topUps().size)
    }

    @Test
    fun `klucz niewiadomej nie wygasa przed uplywem dziesieciu minut`() = runTest(dispatcher) {
        val s = Setup()
        s.wallet.topUpResponseLost = unavailable()
        s.wallet.topUpLandedFailure = unavailable()
        s.tapAndSettle(50, this)
        s.wallet.topUpLandedFailure = null
        s.now += 10 * 60 * 1000L

        s.tapAndSettle(50, this)

        assertEquals(listOf("klucz-1"), s.keys)
        assertEquals(1, s.topUps().size)
    }

    @Test
    fun `odrzucona kwota nie zuzywa klucza ani nie czyta bazy`() = runTest(dispatcher) {
        val s = Setup()

        s.tapAndSettle(1001, this)

        assertTrue(s.keys.isEmpty())
        assertEquals(0, s.wallet.topUpLandedCalls)
        assertEquals(0, s.wallet.topUpWrites)
    }

    @Test
    fun `atrapa sprawdzenia nie widzi cudzego wpisu o tym samym identyfikatorze`() = runTest(dispatcher) {
        val s = Setup()
        s.wallet.saveTransaction(
            com.example.stellare.data.model.WalletTransactionModel(
                transactionId = "wspolny", userId = "ktos-inny", amount = 5, type = WalletTransactionType.TOPUP
            )
        )

        assertFalse(s.wallet.topUpLanded("k1", "wspolny"))
        assertTrue(s.wallet.topUpLanded("ktos-inny", "wspolny"))
        assertNotEquals(s.wallet.topUpLanded("k1", "wspolny"), s.wallet.topUpLanded("ktos-inny", "wspolny"))
    }
}
