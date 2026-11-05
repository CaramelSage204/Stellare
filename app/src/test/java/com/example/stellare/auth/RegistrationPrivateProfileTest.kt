package com.example.stellare.auth

import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.auth.AuthStatus
import com.example.stellare.ui.auth.AuthViewModel
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationPrivateProfileTest {

    private val dispatcher = StandardTestDispatcher()
    private val auth = FakeAuthDataSource().apply { registerResult = Result.success("nowy-uid") }
    private val users = FakeUserDataSource()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AuthViewModel(
        UserRepository(auth, users, FakeFavoriteDataSource(), FakeRatingDataSource(), users.privateProfiles),
        auth
    )

    private fun AuthViewModel.registerAs(role: UserRole = UserRole.PATIENT) = register(
        firstName = "Jan", lastName = "Kowalski", age = 30, gender = "Mężczyzna", phone = "500600700",
        email = "jan@example.com", password = "haslo123", role = role
    )

    @Test
    fun `rejestracja zaklada dokument publiczny i prywatny, a saldo wynosi zero`() = runTest(dispatcher) {
        viewModel().registerAs()
        advanceUntilIdle()

        assertNotNull("brak dokumentu publicznego po rejestracji", users.users.value.singleOrNull { it.id == "nowy-uid" })
        val private = users.privateProfiles.profiles.value["nowy-uid"]
        assertNotNull(
            "konto powstało bez dokumentu prywatnego: portfel pokazuje zero bez możliwości doładowania i rezerwacja płatnej wizyty jest odrzucana",
            private
        )
        assertEquals("saldo startowe musi być zerem zapisanym jawnie", 0, private!!.coinsBalance)
        assertEquals("adres e-mail trafia do dokumentu prywatnego, nie publicznego", "jan@example.com", private.email)
        assertEquals("numer z formularza nie jest potwierdzony, więc nie trafia do profilu", "", private.phone)
    }

    @Test
    fun `rejestracja psychologa tez zaklada oba dokumenty`() = runTest(dispatcher) {
        viewModel().registerAs(UserRole.PSYCHOLOGIST)
        advanceUntilIdle()

        assertNotNull(users.users.value.singleOrNull { it.id == "nowy-uid" })
        assertEquals(0, users.privateProfiles.balanceOf("nowy-uid"))
    }

    @Test
    fun `uzupelnienie profilu po logowaniu przez dostawce zaklada oba dokumenty`() = runTest(dispatcher) {
        val social = FakeAuthDataSource(initialUserId = "g1")
        val vm = AuthViewModel(
            UserRepository(social, users, FakeFavoriteDataSource(), FakeRatingDataSource(), users.privateProfiles),
            social
        )
        backgroundScope.launch { vm.authStatus.collect {} }
        advanceUntilIdle()
        runCurrent()

        vm.completeProfile(UserRole.PATIENT, "Ewa", "Nowak", "41", "Kobieta", "", "")
        advanceUntilIdle()
        runCurrent()

        assertNotNull("brak dokumentu publicznego", users.users.value.singleOrNull { it.id == "g1" })
        assertEquals(
            "profil uzupełniony po logowaniu przez dostawcę musi mieć portfel z saldem 0",
            0,
            users.privateProfiles.balanceOf("g1")
        )
        assertEquals(AuthStatus.LOGGED_IN, vm.authStatus.value)
    }

    @Test
    fun `gdy dokumentu prywatnego nie da sie zalozyc, profil publiczny nie powstaje`() = runTest(dispatcher) {
        users.privateProfiles.createFailure = IllegalStateException("UNAVAILABLE")
        val vm = viewModel()
        vm.registerAs()
        advanceUntilIdle()

        assertTrue(
            "profil publiczny jest sygnałem kompletnego konta: bez portfela nie wolno go zapisać",
            users.users.value.none { it.id == "nowy-uid" }
        )
        assertNotNull(vm.authError.value)
    }
}
