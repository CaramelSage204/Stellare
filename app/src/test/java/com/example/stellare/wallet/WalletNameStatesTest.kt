package com.example.stellare.wallet

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.data.model.NameLookup
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.data.remote.UserDataSource
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.WalletRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.ui.wallet.WalletEntryMapper
import com.example.stellare.ui.wallet.WalletViewModel
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
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WalletNameStatesTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class ControlledUserDataSource(private val inner: FakeUserDataSource) : UserDataSource by inner {
        var gate: CompletableDeferred<Unit>? = null
        var failing = false
        var reads = 0

        override suspend fun getUserOnce(uid: String): UserModel? {
            reads++
            gate?.await()
            if (uid.isEmpty()) return null
            if (failing) throw IOException("brak sieci")
            return inner.getUserOnce(uid)
        }
    }

    private fun tx(id: String, counterparty: String) = WalletTransactionModel(
        transactionId = id, userId = "k1", counterpartyId = counterparty, amount = 80,
        type = WalletTransactionType.REFUND, title = "Zwrot"
    )

    private class Setup(
        val vm: WalletViewModel,
        val wallet: FakeWalletDataSource,
        val users: ControlledUserDataSource,
        val repository: UserRepository
    )

    private fun zbuduj(vararg documents: UserModel): Setup {
        val controlled = ControlledUserDataSource(FakeUserDataSource(documents.toList()))
        val repository = UserRepository(FakeAuthDataSource("k1"), controlled, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(controlled))
        val wallet = FakeWalletDataSource()
        return Setup(WalletViewModel(WalletRepository(wallet), repository), wallet, controlled, repository)
    }

    private fun kotlinx.coroutines.test.TestScope.obserwuj(vm: WalletViewModel) {
        backgroundScope.launch { vm.history.collect {} }
    }

    @Test
    fun `dopoki nazwa sie wczytuje historia mowi ze wczytuje, a nie ze to inny uzytkownik`() = runTest(dispatcher) {
        val s = zbuduj(UserModel(id = "p1", firstName = "Anna", lastName = "Nowak"))
        s.users.gate = CompletableDeferred()
        s.wallet.transactions.value = listOf(tx("a", "p1"))
        obserwuj(s.vm)
        runCurrent()

        assertEquals("w trakcie wczytywania nazwy historia musi to mówić, a nie podawać „inny użytkownik”", "Wizyta u: wczytywanie…", s.vm.history.value.single().counterpartyLine)

        s.users.gate!!.complete(Unit)
        runCurrent()

        assertEquals("po wczytaniu historia musi pokazać nazwisko", "Wizyta u: Anna Nowak", s.vm.history.value.single().counterpartyLine)
    }

    @Test
    fun `brak dokumentu drugiej strony to inny uzytkownik, nie wczytywanie`() = runTest(dispatcher) {
        val s = zbuduj()
        s.wallet.transactions.value = listOf(tx("a", "usuniety"))
        obserwuj(s.vm)
        runCurrent()

        assertEquals("nieistniejący dokument to „inny użytkownik”, nie wczytywanie ani błąd", "Wizyta u: inny użytkownik", s.vm.history.value.single().counterpartyLine)
    }

    @Test
    fun `blad odczytu nazwy mowi ze sie nie udalo, a nie ze to inny uzytkownik`() = runTest(dispatcher) {
        val s = zbuduj(UserModel(id = "p1", firstName = "Anna", lastName = "Nowak"))
        s.users.failing = true
        s.wallet.transactions.value = listOf(tx("a", "p1"))
        obserwuj(s.vm)
        runCurrent()

        assertEquals("błąd odczytu nie może udawać nieistniejącego użytkownika", "Wizyta u: nie udało się wczytać nazwy", s.vm.history.value.single().counterpartyLine)
    }

    @Test
    fun `niepowodzenie odczytu nie jest zapamietane na zawsze`() = runTest(dispatcher) {
        val s = zbuduj(UserModel(id = "p1", firstName = "Anna", lastName = "Nowak"))
        s.users.failing = true
        s.wallet.transactions.value = listOf(tx("a", "p1"))
        obserwuj(s.vm)
        runCurrent()

        s.users.failing = false
        s.wallet.transactions.value = listOf(tx("a", "p1"), tx("b", "p1"))
        runCurrent()

        assertEquals(
            "po chwilowym błędzie następny odczyt musi się powtórzyć, a nie zostać zapamiętany jako brak",
            listOf("Wizyta u: Anna Nowak", "Wizyta u: Anna Nowak"),
            s.vm.history.value.map { it.counterpartyLine }
        )
    }

    @Test
    fun `po nieudanym odczycie ponowienie wczytuje nazwe bez zmiany listy transakcji`() = runTest(dispatcher) {
        val s = zbuduj(UserModel(id = "p1", firstName = "Anna", lastName = "Nowak"))
        s.users.failing = true
        s.wallet.transactions.value = listOf(tx("a", "p1"))
        obserwuj(s.vm)
        runCurrent()
        assertEquals("nieudany odczyt musi być oznaczony, inaczej ekran nie pokaże przycisku ponowienia", true, s.vm.history.value.single().nameUnavailable)

        s.users.failing = false
        s.vm.retryNames()
        runCurrent()

        assertEquals(
            "kliknięcie ponowienia po nieudanym odczycie ma pokazać nazwisko",
            "Wizyta u: Anna Nowak",
            s.vm.history.value.single().counterpartyLine
        )
        assertEquals("po wczytaniu nazwy przycisk ponowienia ma zniknąć", false, s.vm.history.value.single().nameUnavailable)
    }

    @Test
    fun `ponowienie nie odpytuje ponownie nazw ktore juz sa ustalone`() = runTest(dispatcher) {
        val s = zbuduj(UserModel(id = "p1", firstName = "Anna", lastName = "Nowak"))
        s.wallet.transactions.value = listOf(tx("a", "p1"))
        obserwuj(s.vm)
        runCurrent()
        val readsAfterFirst = s.users.reads

        s.vm.retryNames()
        runCurrent()

        assertEquals("ponowienie ma dotyczyć tylko nazw, których nie udało się wczytać", readsAfterFirst, s.users.reads)
    }

    @Test
    fun `ustalona nazwa nie jest odpytywana drugi raz`() = runTest(dispatcher) {
        val s = zbuduj(UserModel(id = "p1", firstName = "Anna", lastName = "Nowak"))
        s.wallet.transactions.value = listOf(tx("a", "p1"))
        obserwuj(s.vm)
        runCurrent()
        val readsAfterFirst = s.users.reads

        s.wallet.transactions.value = listOf(tx("a", "p1"), tx("b", "p1"))
        runCurrent()

        assertEquals("ustalona nazwa ma być w pamięci, nie czytana z serwera przy każdej zmianie listy", readsAfterFirst, s.users.reads)
    }

    @Test
    fun `repozytorium rozroznia nazwe, pustke i niepowodzenie`() = runTest(dispatcher) {
        val s = zbuduj(UserModel(id = "p1", firstName = "Anna", lastName = "Nowak"), UserModel(id = "bez", firstName = "", lastName = ""))
        val ok = s.repository.lookupNames(setOf("p1", "nikt", "bez"))
        s.users.failing = true
        val failed = s.repository.lookupNames(setOf("inny"))

        assertEquals("istniejący użytkownik z nazwiskiem", NameLookup.Known("Anna Nowak"), ok.getValue("p1"))
        assertEquals("brak dokumentu to pustka", NameLookup.Absent, ok.getValue("nikt"))
        assertEquals("dokument bez imienia i nazwiska to pustka", NameLookup.Absent, ok.getValue("bez"))
        assertEquals("wyjątek to niepowodzenie, nie pustka", NameLookup.Unavailable, failed.getValue("inny"))
    }

    @Test
    fun `mapper pokazuje cztery stany drugiej strony osobno`() {
        val t = tx("a", "p1")
        assertEquals("mapper: stan wczytywania", "Wizyta u: wczytywanie…", WalletEntryMapper.map(t, NameLookup.Loading).counterpartyLine)
        assertEquals("mapper: znana nazwa", "Wizyta u: Anna Nowak", WalletEntryMapper.map(t, NameLookup.Known("Anna Nowak")).counterpartyLine)
        assertEquals("mapper: pustka", "Wizyta u: inny użytkownik", WalletEntryMapper.map(t, NameLookup.Absent).counterpartyLine)
        assertEquals("mapper: niepowodzenie", "Wizyta u: nie udało się wczytać nazwy", WalletEntryMapper.map(t, NameLookup.Unavailable).counterpartyLine)
    }
}
