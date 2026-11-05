package com.example.stellare.auth

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.auth.AuthStatus
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.session.RegistrationRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.google.firebase.firestore.FirebaseFirestoreException
import com.example.stellare.ui.UserFacingError

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelProfileCompletionTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(auth: FakeAuthDataSource, users: FakeUserDataSource): AuthViewModel =
        AuthViewModel(UserRepository(auth, users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)), auth)

    @Test
    fun `zalogowany przez Google bez dokumentu ma status uzupelniania profilu a nie wylogowany`() = runTest(dispatcher) {
        val vm = viewModel(FakeAuthDataSource(initialUserId = "g1"), FakeUserDataSource())
        backgroundScope.launch { vm.authStatus.collect {} }

        advanceUntilIdle()
        runCurrent()

        assertEquals(AuthStatus.PROFILE_INCOMPLETE, vm.authStatus.value)
    }

    @Test
    fun `zalogowany z dokumentem nie wymaga uzupelnienia`() = runTest(dispatcher) {
        val vm = viewModel(FakeAuthDataSource(initialUserId = "u1"), FakeUserDataSource(listOf(UserModel(id = "u1"))))
        backgroundScope.launch { vm.authStatus.collect {} }

        advanceUntilIdle()
        runCurrent()

        assertEquals(AuthStatus.LOGGED_IN, vm.authStatus.value)
    }

    @Test
    fun `wylogowany nie wymaga uzupelnienia`() = runTest(dispatcher) {
        val vm = viewModel(FakeAuthDataSource(), FakeUserDataSource())
        backgroundScope.launch { vm.authStatus.collect {} }

        advanceUntilIdle()
        runCurrent()

        assertEquals(AuthStatus.LOGGED_OUT, vm.authStatus.value)
    }

    @Test
    fun `uzupelnienie profilu zapisuje dokument z wybrana rola i wpuszcza do aplikacji`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        val vm = viewModel(FakeAuthDataSource(initialUserId = "g1"), users)
        backgroundScope.launch { vm.authStatus.collect {} }
        advanceUntilIdle()
        runCurrent()

        vm.completeProfile(UserRole.PSYCHOLOGIST, "Ewa", "Nowak", "41", "Kobieta", "Opis", "180")
        advanceUntilIdle()
        runCurrent()

        val zapisany = users.users.value.single { it.id == "g1" }
        assertEquals(UserRole.PSYCHOLOGIST, zapisany.role)
        assertEquals("Ewa", zapisany.firstName)
        assertEquals("Nowak", zapisany.lastName)
        assertEquals(41, zapisany.age)
        assertEquals("Kobieta", zapisany.gender)
        assertEquals("Opis", zapisany.bio)
        assertEquals(180.0, zapisany.pricePerSession, 0.0)
        assertFalse(zapisany.isVerified)
        assertEquals(AuthStatus.LOGGED_IN, vm.authStatus.value)
        assertNull(vm.authError.value)
    }

    @Test
    fun `uzupelnienie profilu psychologa bez dodatniej ceny niczego nie zapisuje`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        val vm = viewModel(FakeAuthDataSource(initialUserId = "g1"), users)
        backgroundScope.launch { vm.authStatus.collect {} }
        advanceUntilIdle()
        runCurrent()

        vm.completeProfile(UserRole.PSYCHOLOGIST, "Ewa", "Nowak", "41", "Kobieta", "", "0")
        advanceUntilIdle()
        runCurrent()

        assertTrue("bez dodatniej ceny dokument nie moze powstac", users.users.value.isEmpty())
        assertEquals(RegistrationRules.PRICE_INVALID, vm.authError.value)
        assertEquals(AuthStatus.PROFILE_INCOMPLETE, vm.authStatus.value)
    }

    @Test
    fun `uzupelnienie profilu pacjenta zapisuje zerowa cene`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        val vm = viewModel(FakeAuthDataSource(initialUserId = "g1"), users)
        advanceUntilIdle()
        runCurrent()

        vm.completeProfile(UserRole.PATIENT, "Jan", "Kowalski", "30", "Mezczyzna", "", "999")
        advanceUntilIdle()
        runCurrent()

        val zapisany = users.users.value.single()
        assertEquals(UserRole.PATIENT, zapisany.role)
        assertEquals(0.0, zapisany.pricePerSession, 0.0)
    }

    @Test
    fun `uzupelnienie profilu bez zalogowanego uzytkownika zglasza polski blad`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        val vm = viewModel(FakeAuthDataSource(), users)

        vm.completeProfile(UserRole.PATIENT, "Jan", "Kowalski", "30", "Mezczyzna", "", "")
        advanceUntilIdle()
        runCurrent()

        assertTrue(users.users.value.isEmpty())
        assertNotNull(vm.authError.value)
    }

    @Test
    fun `rejestracja mailem nie przechodzi przez status uzupelniania profilu`() = runTest(dispatcher) {
        val auth = FakeAuthDataSource().apply { registerResult = Result.success("nowy") }
        val vm = viewModel(auth, FakeUserDataSource())
        val statusy = mutableListOf<AuthStatus>()
        backgroundScope.launch { vm.authStatus.collect { statusy.add(it) } }
        advanceUntilIdle()
        runCurrent()

        vm.register("Jan", "Kowalski", 30, "Mezczyzna", "", "jan@example.com", "haslo123", UserRole.PATIENT)
        advanceUntilIdle()
        runCurrent()

        assertFalse(
            "rejestracja mailem tworzy konto i dokument razem, nie moze mignac ekranem uzupelniania: $statusy",
            statusy.contains(AuthStatus.PROFILE_INCOMPLETE)
        )
        assertEquals(AuthStatus.LOGGED_IN, statusy.last())
    }

    private class NamedAuthDataSource(delegate: FakeAuthDataSource, private val name: String?) : AuthDataSource by delegate {
        override fun getCurrentUserDisplayNameOnce(): String? = name
    }

    @Test
    fun `podpowiedz imienia pochodzi z konta Google a brak nazwy daje puste pola`() {
        val users = FakeUserDataSource()
        val z = NamedAuthDataSource(FakeAuthDataSource("g1"), "Ewa Nowak")
        assertEquals("Ewa" to "Nowak", AuthViewModel(UserRepository(z, users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)), z).suggestedName())
        val bez = FakeAuthDataSource("g1")
        assertEquals("" to "", AuthViewModel(UserRepository(bez, users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)), bez).suggestedName())
    }

    @Test
    fun `uzupelnienie profilu bez polaczenia mowi o braku polaczenia i niczego nie zapisuje`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        users.saveFailure = FirebaseFirestoreException("Failed to get document because the client is offline.", FirebaseFirestoreException.Code.UNAVAILABLE)
        val vm = viewModel(FakeAuthDataSource(initialUserId = "g1"), users)
        backgroundScope.launch { vm.authStatus.collect {} }
        advanceUntilIdle()
        runCurrent()

        vm.completeProfile(UserRole.PATIENT, "Jan", "Kowalski", "30", "Mezczyzna", "", "0")
        advanceUntilIdle()
        runCurrent()

        assertEquals(
            "transakcja nie kolejkuje zapisu offline, więc użytkownik ma usłyszeć prawdziwą przyczynę",
            UserFacingError.NO_CONNECTION,
            vm.authError.value
        )
        assertTrue(users.users.value.none { it.id == "g1" })
    }
}
