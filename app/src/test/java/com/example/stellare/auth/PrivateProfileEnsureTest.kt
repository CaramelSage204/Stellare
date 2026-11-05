package com.example.stellare.auth

import com.example.stellare.data.model.PrivateProfile
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivateProfileEnsureTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun users(withPrivate: PrivateProfile?): FakeUserDataSource {
        val users = FakeUserDataSource(listOf(UserModel(id = "u1", firstName = "Ewa")))
        users.privateProfiles.profiles.value = if (withPrivate == null) emptyMap() else mapOf("u1" to withPrivate)
        return users
    }

    private fun viewModel(users: FakeUserDataSource, uid: String? = "u1"): AuthViewModel {
        val auth = FakeAuthDataSource(initialUserId = uid)
        return AuthViewModel(UserRepository(auth, users, FakeFavoriteDataSource(), FakeRatingDataSource(), users.privateProfiles), auth)
    }

    @Test
    fun `logowanie na konto bez dokumentu prywatnego zaklada go z saldem zero`() = runTest(dispatcher) {
        val users = users(withPrivate = null)
        val vm = viewModel(users)
        backgroundScope.launch { vm.authStatus.collect {} }
        advanceUntilIdle()
        runCurrent()

        assertEquals("konto sprzed przeniesienia pól zostałoby na stałe bez portfela", PrivateProfile(), users.privateProfiles.profiles.value["u1"])
    }

    @Test
    fun `logowanie na konto z dokumentem prywatnym nie zeruje salda ani nie rusza danych kontaktowych`() = runTest(dispatcher) {
        val existing = PrivateProfile(phone = "+48500600700", email = "ewa@example.com", coinsBalance = 500)
        val users = users(withPrivate = existing)
        val vm = viewModel(users)
        backgroundScope.launch { vm.authStatus.collect {} }
        advanceUntilIdle()
        runCurrent()

        assertEquals("logowanie wyzerowało istniejące saldo", existing, users.privateProfiles.profiles.value["u1"])
        assertEquals("kontrola przyrządu: operacja założenia w ogóle została wywołana", listOf("u1"), users.privateProfiles.createdFor)
    }

    @Test
    fun `kolejne emisje profilu nie zakladaja dokumentu ponownie`() = runTest(dispatcher) {
        val users = users(withPrivate = null)
        val vm = viewModel(users)
        backgroundScope.launch { vm.authStatus.collect {} }
        advanceUntilIdle()
        runCurrent()

        users.saveUser(UserModel(id = "u1", firstName = "Ewa", bio = "nowe"))
        advanceUntilIdle()
        runCurrent()
        users.saveUser(UserModel(id = "u1", firstName = "Ewa", bio = "jeszcze nowsze"))
        advanceUntilIdle()
        runCurrent()

        assertEquals(listOf("u1"), users.privateProfiles.createdFor)
    }

    @Test
    fun `nieudane zalozenie jest ponawiane przy nastepnej emisji profilu`() = runTest(dispatcher) {
        val users = users(withPrivate = null)
        users.privateProfiles.createFailure = IllegalStateException("UNAVAILABLE")
        val vm = viewModel(users)
        backgroundScope.launch { vm.authStatus.collect {} }
        advanceUntilIdle()
        runCurrent()
        assertNull(users.privateProfiles.profiles.value["u1"])

        users.privateProfiles.createFailure = null
        users.saveUser(UserModel(id = "u1", firstName = "Ewa", bio = "zmiana"))
        advanceUntilIdle()
        runCurrent()

        assertEquals(PrivateProfile(), users.privateProfiles.profiles.value["u1"])
    }

    @Test
    fun `nieudane usuwanie konta, ktore zdjelo dokument prywatny, nie zostawia sesji bez portfela`() = runTest(dispatcher) {
        val users = users(withPrivate = PrivateProfile(email = "ewa@example.com", coinsBalance = 0))
        val auth = FakeAuthDataSource(initialUserId = "u1")
        val repository = UserRepository(auth, users, FakeFavoriteDataSource(), FakeRatingDataSource(), users.privateProfiles)
        val vm = AuthViewModel(repository, auth)
        backgroundScope.launch { vm.authStatus.collect {} }
        advanceUntilIdle()
        runCurrent()
        assertEquals("kontrola: przed usuwaniem dokument istnieje", PrivateProfile(email = "ewa@example.com"), users.privateProfiles.profiles.value["u1"])

        repository.holdProfileObservation()
        advanceUntilIdle()
        users.privateProfiles.deletePrivateProfile("u1")
        repository.releaseProfileObservation()
        advanceUntilIdle()
        runCurrent()

        assertEquals(
            "po nieudanym usuwaniu (zniknął dokument prywatny, został publiczny) konto zostało do końca sesji bez portfela",
            PrivateProfile(),
            users.privateProfiles.profiles.value["u1"]
        )
    }

    @Test
    fun `konto bez profilu publicznego nie dostaje dokumentu prywatnego od logowania`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        val vm = viewModel(users, uid = "g1")
        backgroundScope.launch { vm.authStatus.collect {} }
        advanceUntilIdle()
        runCurrent()

        assertNull("dokument zakłada uzupełnienie profilu, nie samo logowanie", users.privateProfiles.profiles.value["g1"])
        assertEquals(emptyList<String>(), users.privateProfiles.createdFor)
    }
}
