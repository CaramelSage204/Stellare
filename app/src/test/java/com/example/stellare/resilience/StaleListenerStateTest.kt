package com.example.stellare.resilience

import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.WalletRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.ui.wallet.WalletViewModel
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * R8: po unieważnieniu tokenu nasłuch logował błąd i wychodził, a ostatnia dobra migawka zostawała
 * na ekranie jako aktualna. Dane nadal wolno pokazać — nie wolno tylko twierdzić, że są aktualne.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StaleListenerStateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun przelew(id: String) = WalletTransactionModel(
        transactionId = id, userId = "k1", counterpartyId = null, amount = 80,
        type = WalletTransactionType.TOPUP, title = "Doładowanie"
    )

    private class Setup(val vm: WalletViewModel, val wallet: FakeWalletDataSource)

    private fun zbuduj(): Setup {
        val users = FakeUserDataSource()
        val repository = UserRepository(
            FakeAuthDataSource("k1"), users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)
        )
        val wallet = FakeWalletDataSource()
        return Setup(WalletViewModel(WalletRepository(wallet), repository), wallet)
    }

    private fun odmowa() =
        FirebaseFirestoreException("odmowa regul", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    @Test
    fun `trwala odmowa nasluchu przestaje przedstawiac ostatnia migawke jako aktualna`() = runTest(dispatcher) {
        val s = zbuduj()
        s.wallet.transactions.value = listOf(przelew("a"))
        backgroundScope.launch { s.vm.transactions.collect {} }
        backgroundScope.launch { s.vm.historyStaleMessage.collect {} }
        runCurrent()
        assertEquals("przed awaria historia ma byc wczytana", 1, s.vm.transactions.value.size)
        assertNull("dopoki dane naplywaja, nie ma o czym informowac", s.vm.historyStaleMessage.value)

        s.wallet.listenerFailure = odmowa()
        s.wallet.transactions.value = listOf(przelew("a"), przelew("b"))
        runCurrent()

        assertNotNull(
            "padniety nasluch musi trafic do stanu ekranu; sam log zostawia ostatnia dobra migawke " +
                "na ekranie jako aktualna, a odswiezanie niczego nie zmienia",
            s.vm.historyStaleMessage.value
        )
        assertEquals(
            "Nie udało się odświeżyć historii portfela. Pokazane pozycje mogą być nieaktualne.",
            s.vm.historyStaleMessage.value
        )
    }

    @Test
    fun `ponowienie po odmowie nasluchu wczytuje dane i gasi komunikat`() = runTest(dispatcher) {
        val s = zbuduj()
        s.wallet.transactions.value = listOf(przelew("a"))
        backgroundScope.launch { s.vm.transactions.collect {} }
        backgroundScope.launch { s.vm.historyStaleMessage.collect {} }
        runCurrent()
        s.wallet.listenerFailure = odmowa()
        s.wallet.transactions.value = listOf(przelew("a"), przelew("b"))
        runCurrent()
        assertNotNull("warunek wstepny: komunikat o nieaktualnosci jest ustawiony", s.vm.historyStaleMessage.value)

        s.wallet.listenerFailure = null
        s.vm.retryHistory()
        runCurrent()

        assertEquals("ponowienie ma wczytac pelna historie", 2, s.vm.transactions.value.size)
        assertNull(
            "po napłynięciu danych komunikat ma zgasnąć, bo dane znowu są aktualne",
            s.vm.historyStaleMessage.value
        )
    }

    @Test
    fun `komunikat nie gasnie od samego kliknięcia ponowienia, dopoki nowy nasluch nie odpowiedzial`() = runTest(dispatcher) {
        val s = zbuduj()
        s.wallet.transactions.value = listOf(przelew("a"))
        backgroundScope.launch { s.vm.transactions.collect {} }
        backgroundScope.launch { s.vm.historyStaleMessage.collect {} }
        runCurrent()
        s.wallet.listenerFailure = odmowa()
        s.wallet.transactions.value = listOf(przelew("a"), przelew("b"))
        runCurrent()
        assertNotNull("warunek wstepny: komunikat o nieaktualnosci jest ustawiony", s.vm.historyStaleMessage.value)

        s.wallet.listenerFailure = null
        s.wallet.listenerGate = CompletableDeferred()
        s.vm.retryHistory()
        runCurrent()

        assertNotNull(
            "miedzy kliknieciem ponowienia a odpowiedzia nowego nasluchu na ekranie nadal stoi stara " +
                "migawka; zgaszenie komunikatu w chwili kliknięcia przedstawia ja jako aktualna, " +
                "choc nikt jeszcze niczego nie potwierdzil",
            s.vm.historyStaleMessage.value
        )

        s.wallet.listenerGate!!.complete(Unit)
        runCurrent()
        assertNull("gdy nowy nasluch odpowiedzial, komunikat ma zgasnac", s.vm.historyStaleMessage.value)
    }
}
