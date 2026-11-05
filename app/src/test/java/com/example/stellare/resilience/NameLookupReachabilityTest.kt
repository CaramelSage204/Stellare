package com.example.stellare.resilience

import com.example.stellare.data.model.NameLookup
import com.example.stellare.data.model.UserModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Przycisk „Nie udało się wczytać części nazw" pokazuje się wtedy i tylko wtedy, gdy jakiś wpis
 * ma `nameUnavailable`. Ten test prowadzi awarię od granicy źródła danych — tego samego miejsca,
 * w którym stoi `FirestoreUserDataSource.getUserOnce` — aż do tej flagi, bo dopóki odczyt połykał
 * każdy wyjątek i zwracał `null`, cała ta droga kończyła się na „inny użytkownik".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NameLookupReachabilityTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val druga = UserModel(id = "p1", firstName = "Anna", lastName = "Nowak")

    private fun przelew(id: String, counterparty: String?) = WalletTransactionModel(
        transactionId = id, userId = "k1", counterpartyId = counterparty, amount = 80,
        type = WalletTransactionType.REFUND, title = "Zwrot"
    )

    private class Setup(
        val vm: WalletViewModel,
        val users: FakeUserDataSource,
        val wallet: FakeWalletDataSource,
        val repository: UserRepository
    )

    private fun zbuduj(vararg documents: UserModel): Setup {
        val users = FakeUserDataSource(documents.toList())
        val repository = UserRepository(
            FakeAuthDataSource("k1"), users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)
        )
        val wallet = FakeWalletDataSource()
        return Setup(WalletViewModel(WalletRepository(wallet), repository), users, wallet, repository)
    }

    @Test
    fun `nieudany odczyt nazwy zapala flage, ktora wlacza przycisk ponowienia`() = runTest(dispatcher) {
        val s = zbuduj(druga)
        s.users.readFailure = IOException("odczyt nazwy padl")
        s.wallet.transactions.value = listOf(przelew("a", "p1"))
        backgroundScope.launch { s.vm.history.collect {} }
        runCurrent()

        val entry = s.vm.history.value.single()
        assertTrue(
            "nieudany odczyt nazwy musi zapalic nameUnavailable, inaczej ekran portfela nigdy nie " +
                "pokaze przycisku ponowienia i awaria odczytu udaje „inny uzytkownik”",
            entry.nameUnavailable
        )
        assertEquals(
            "wiersz ma mowic, ze nazwy nie udalo sie wczytac, a nie ze to inny uzytkownik",
            "Wizyta u: nie udało się wczytać nazwy",
            entry.counterpartyLine
        )
    }

    @Test
    fun `brak dokumentu nie zapala flagi, bo nie ma czego ponawiac`() = runTest(dispatcher) {
        val s = zbuduj()
        s.wallet.transactions.value = listOf(przelew("a", "usuniety"))
        backgroundScope.launch { s.vm.history.collect {} }
        runCurrent()

        val entry = s.vm.history.value.single()
        assertFalse(
            "nieistniejace konto to nie awaria odczytu: przycisk ponowienia nie ma tu czego ponawiac",
            entry.nameUnavailable
        )
        assertEquals("Wizyta u: inny użytkownik", entry.counterpartyLine)
    }

    @Test
    fun `repozytorium rozdziela pustke od awarii na tej samej granicy zrodla danych`() = runTest(dispatcher) {
        val s = zbuduj(druga)

        val znane = s.repository.lookupNames(setOf("p1", "nikogo-takiego"))
        s.users.readFailure = IOException("odczyt nazwy padl")
        val nieudane = s.repository.lookupNames(setOf("inny"))

        assertEquals("istniejacy dokument", NameLookup.Known("Anna Nowak"), znane.getValue("p1"))
        assertEquals(
            "brak dokumentu to Absent",
            NameLookup.Absent,
            znane.getValue("nikogo-takiego")
        )
        assertEquals(
            "wyjatek z odczytu to Unavailable, nie Absent; zrownanie ich czyni przycisk ponowienia martwym kodem",
            NameLookup.Unavailable,
            nieudane.getValue("inny")
        )
    }
}
