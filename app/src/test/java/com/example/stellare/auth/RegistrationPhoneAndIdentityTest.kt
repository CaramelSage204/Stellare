package com.example.stellare.auth

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.session.RegistrationRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationPhoneAndIdentityTest {

    private val dispatcher = StandardTestDispatcher()
    private val auth = FakeAuthDataSource().apply { registerResult = Result.success("nowy-uid") }
    private val users = FakeUserDataSource()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AuthViewModel(UserRepository(auth, users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)), auth)

    private fun AuthViewModel.registerWith(
        firstName: String = "Jan",
        lastName: String = "Kowalski",
        age: Int = 30,
        phone: String = ""
    ) = register(
        firstName = firstName,
        lastName = lastName,
        age = age,
        gender = "Mężczyzna",
        phone = phone,
        email = "jan@example.com",
        password = "haslo123",
        role = UserRole.PATIENT
    )

    @Test
    fun `rejestracja nie zapisuje numeru w dokumencie, bo nie jest jeszcze potwierdzony`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.registerWith(phone = "500600700")
        advanceUntilIdle()
        runCurrent()

        val saved = users.privateProfiles.profiles.value.getValue("nowy-uid")
        assertEquals("numer wpisany w rejestracji nie jest potwierdzony, wiec nie moze trafic do profilu", "", saved.phone)
    }

    @Test
    fun `rejestracja z poprawnym numerem proponuje jego potwierdzenie w formacie miedzynarodowym`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.registerWith(phone = "500 600 700")
        advanceUntilIdle()
        runCurrent()

        assertEquals("+48500600700", vm.phoneVerificationOffer.value)
    }

    @Test
    fun `rejestracja bez numeru nie proponuje potwierdzenia`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.registerWith(phone = "")
        advanceUntilIdle()
        runCurrent()

        assertNull(vm.phoneVerificationOffer.value)
    }

    @Test
    fun `rejestracja z numerem nie do potwierdzenia nie proponuje niczego`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.registerWith(phone = "12345")
        advanceUntilIdle()
        runCurrent()

        assertNull(vm.phoneVerificationOffer.value)
    }

    @Test
    fun `nieudana rejestracja nie proponuje potwierdzenia`() = runTest(dispatcher) {
        auth.registerResult = Result.failure(Exception("zajety"))
        val vm = viewModel()
        vm.registerWith(phone = "500600700")
        advanceUntilIdle()
        runCurrent()

        assertNull(vm.phoneVerificationOffer.value)
    }

    @Test
    fun `propozycja jest jednorazowa`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.registerWith(phone = "500600700")
        advanceUntilIdle()
        runCurrent()

        assertEquals("+48500600700", vm.takePhoneVerificationOffer())
        assertNull(vm.takePhoneVerificationOffer())
        assertNull(vm.phoneVerificationOffer.value)
    }

    @Test
    fun `odrzucenie propozycji ja czysci`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.registerWith(phone = "500600700")
        advanceUntilIdle()
        runCurrent()
        vm.dismissPhoneVerificationOffer()

        assertNull(vm.phoneVerificationOffer.value)
    }

    @Test
    fun `rejestracja z zerowym wiekiem nie tworzy konta`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.registerWith(age = 0)
        advanceUntilIdle()
        runCurrent()

        assertEquals(RegistrationRules.AGE_INVALID, vm.authError.value)
        assertTrue("konto z wiekiem 0 nie moze powstac: ${users.users.value}", users.users.value.isEmpty())
    }

    @Test
    fun `rejestracja z pustym imieniem lub nazwiskiem nie tworzy konta`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.registerWith(firstName = "  ")
        advanceUntilIdle()
        runCurrent()
        assertEquals(RegistrationRules.FIRST_NAME_REQUIRED, vm.authError.value)

        vm.registerWith(lastName = "")
        advanceUntilIdle()
        runCurrent()
        assertEquals(RegistrationRules.LAST_NAME_REQUIRED, vm.authError.value)
        assertTrue(users.users.value.isEmpty())
    }
}
