package com.example.stellare.auth

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.auth.AuthStatus
import com.example.stellare.ui.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun authViewModelNaAtrapach(): AuthViewModel {
        val userRepository = UserRepository(FakeAuthDataSource(), FakeUserDataSource(), FakeFavoriteDataSource())
        return AuthViewModel(userRepository, FakeAuthDataSource())
    }

    private fun authViewModel(
        authDataSource: FakeAuthDataSource = FakeAuthDataSource(),
        userDataSource: FakeUserDataSource = FakeUserDataSource()
    ): AuthViewModel {
        val userRepository = UserRepository(authDataSource, userDataSource, FakeFavoriteDataSource())
        return AuthViewModel(userRepository, authDataSource)
    }

    @Test
    fun `czyszczenie bledu zeruje komunikat`() = runTest(dispatcher) {
        val vm = authViewModelNaAtrapach()
        vm.clearAuthError()
        assertNull(vm.authError.value)
    }

    @Test
    fun `rejestracja tworzy dokument uzytkownika z podanymi danymi i ustawia showTutorialPrompt`() = runTest(dispatcher) {
        val authDataSource = FakeAuthDataSource().apply { registerResult = Result.success("nowy-uid") }
        val userDataSource = FakeUserDataSource()
        val vm = authViewModel(authDataSource, userDataSource)

        vm.register(
            firstName = "Jan",
            lastName = "Kowalski",
            age = 30,
            gender = "Mężczyzna",
            phone = "500600700",
            email = "jan@example.com",
            password = "haslo123",
            role = UserRole.PATIENT
        )
        advanceUntilIdle()

        val zapisany = userDataSource.users.value.single { it.id == "nowy-uid" }
        assertEquals("Jan", zapisany.firstName)
        assertEquals("Kowalski", zapisany.lastName)
        assertEquals(30, zapisany.age)
        assertEquals("jan@example.com", zapisany.email)
        assertTrue(vm.showTutorialPrompt.value)
        assertNull(vm.authError.value)
    }

    @Test
    fun `rejestracja przy bledzie zrodla ustawia authError i nie tworzy dokumentu`() = runTest(dispatcher) {
        val authDataSource = FakeAuthDataSource().apply {
            registerResult = Result.failure(Exception("E-mail zajęty"))
        }
        val userDataSource = FakeUserDataSource()
        val vm = authViewModel(authDataSource, userDataSource)

        vm.register(
            firstName = "Jan",
            lastName = "Kowalski",
            age = 30,
            gender = "Mężczyzna",
            phone = "500600700",
            email = "jan@example.com",
            password = "haslo123",
            role = UserRole.PATIENT
        )
        advanceUntilIdle()

        assertEquals("Błąd rejestracji: E-mail zajęty", vm.authError.value)
        assertTrue(userDataSource.users.value.isEmpty())
        assertFalse(vm.showTutorialPrompt.value)
    }

    @Test
    fun `logowanie przy istniejacym dokumencie uzytkownika nie ustawia bledu`() = runTest(dispatcher) {
        val authDataSource = FakeAuthDataSource().apply { signInResult = Result.success("u1") }
        val userDataSource = FakeUserDataSource(listOf(UserModel(id = "u1", firstName = "Anna")))
        val vm = authViewModel(authDataSource, userDataSource)

        vm.login("anna@example.com", "haslo123")
        advanceUntilIdle()

        assertNull(vm.authError.value)
    }

    @Test
    fun `logowanie przy braku dokumentu ustawia komunikat o braku danych`() = runTest(dispatcher) {
        val authDataSource = FakeAuthDataSource().apply { signInResult = Result.success("u1") }
        val vm = authViewModel(authDataSource, FakeUserDataSource())

        vm.login("anna@example.com", "haslo123")
        advanceUntilIdle()

        assertEquals("Nie znaleziono danych użytkownika w bazie.", vm.authError.value)
    }

    @Test
    fun `logowanie przy bledzie zrodla ustawia authError`() = runTest(dispatcher) {
        val authDataSource = FakeAuthDataSource().apply {
            signInResult = Result.failure(Exception("Błędne hasło"))
        }
        val vm = authViewModel(authDataSource, FakeUserDataSource())

        vm.login("anna@example.com", "zlehaslo")
        advanceUntilIdle()

        assertEquals("Błąd logowania: Błędne hasło", vm.authError.value)
    }

    @Test
    fun `authStatus przechodzi na LOGGED_IN po udanej rejestracji i na LOGGED_OUT po logout`() = runTest(dispatcher) {
        val authDataSource = FakeAuthDataSource().apply { registerResult = Result.success("nowy-uid") }
        val vm = authViewModel(authDataSource, FakeUserDataSource())
        backgroundScope.launch { vm.authStatus.collect {} }

        vm.register(
            firstName = "Jan",
            lastName = "Kowalski",
            age = 30,
            gender = "Mężczyzna",
            phone = "500600700",
            email = "jan@example.com",
            password = "haslo123",
            role = UserRole.PATIENT
        )
        advanceUntilIdle()

        assertEquals(AuthStatus.LOGGED_IN, vm.authStatus.value)

        vm.logout()
        advanceUntilIdle()

        assertEquals(AuthStatus.LOGGED_OUT, vm.authStatus.value)
    }

    @Test
    fun `authStatus jest nieznany dopoki zrodlo nie odpowiedzialo, mimo ze uzytkownik jest juz zalogowany`() = runTest(dispatcher) {
        val authDataSource = FakeAuthDataSource(initialUserId = "u1")
        val userDataSource = FakeUserDataSource(listOf(UserModel(id = "u1", firstName = "Anna")))
        val vm = authViewModel(authDataSource, userDataSource)
        backgroundScope.launch { vm.authStatus.collect {} }

        assertEquals(AuthStatus.UNKNOWN, vm.authStatus.value)

        advanceUntilIdle()

        assertEquals(AuthStatus.LOGGED_IN, vm.authStatus.value)
    }

    @Test
    fun `authStatus po limicie czasu bez odpowiedzi zrodla przechodzi na LOGGED_OUT`() = runTest(dispatcher) {
        val userRepository = UserRepository(
            NeverRespondingAuthDataSource(),
            FakeUserDataSource(),
            FakeFavoriteDataSource()
        )
        val vm = AuthViewModel(userRepository, NeverRespondingAuthDataSource())
        backgroundScope.launch { vm.authStatus.collect {} }

        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(AuthStatus.UNKNOWN, vm.authStatus.value)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(AuthStatus.LOGGED_OUT, vm.authStatus.value)
    }

    private class NeverRespondingAuthDataSource : AuthDataSource {
        override val currentUserId: Flow<String?> = flow { awaitCancellation() }
        override fun getCurrentUserIdOnce(): String? = null
        override suspend fun registerWithEmail(email: String, password: String): String? = null
        override suspend fun signInWithEmail(email: String, password: String): String? = null
        override suspend fun signInWithGoogle(idToken: String): String? = null
        override fun signOut() {}
    }
}
