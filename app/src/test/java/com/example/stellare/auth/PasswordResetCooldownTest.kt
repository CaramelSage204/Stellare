package com.example.stellare.auth

import com.example.stellare.fakes.FakePrivateProfileDataSource
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.auth.PasswordResetMessages
import com.example.stellare.ui.auth.PasswordResetState
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class CountingResetSource(
    private val delegate: FakeAuthDataSource = FakeAuthDataSource()
) : AuthDataSource by delegate {
    var sendCount = 0
    var failure: Exception? = null

    override suspend fun sendPasswordResetEmail(email: String) {
        sendCount++
        failure?.let { throw it }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordResetCooldownTest {

    private val dispatcher = StandardTestDispatcher()
    private val email = "ewa@example.com"

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(source: CountingResetSource): AuthViewModel {
        val auth = FakeAuthDataSource()
        return AuthViewModel(
            UserRepository(auth, FakeUserDataSource(), FakeFavoriteDataSource(), FakeRatingDataSource(), FakePrivateProfileDataSource()),
            source,
            clock = { dispatcher.scheduler.currentTime }
        )
    }

    private fun TestScope.submit(vm: AuthViewModel, address: String = email) {
        vm.sendPasswordReset(address)
        runCurrent()
    }

    @Test
    fun `po udanej wysylce odliczanie startuje od 60 sekund`() = runTest(dispatcher) {
        val vm = viewModel(CountingResetSource())
        submit(vm)

        assertEquals("po wysylce przycisk ma byc zablokowany na 60 s, a pozostalo: ${vm.resetCooldownSeconds.value}", 60, vm.resetCooldownSeconds.value)
    }

    @Test
    fun `odliczanie maleje wraz z uplywem czasu`() = runTest(dispatcher) {
        val vm = viewModel(CountingResetSource())
        submit(vm)
        advanceTimeBy(20_000)
        runCurrent()

        assertEquals("po 20 s ma zostac 40 s, a zostalo: ${vm.resetCooldownSeconds.value}", 40, vm.resetCooldownSeconds.value)
    }

    @Test
    fun `w trakcie odliczania druga wysylka nie dociera do Firebase`() = runTest(dispatcher) {
        val source = CountingResetSource()
        val vm = viewModel(source)
        submit(vm)
        advanceTimeBy(10_000)
        submit(vm)

        assertEquals("w trakcie odliczania nie wolno wysylac drugiego maila, a wyslano: ${source.sendCount}", 1, source.sendCount)
    }

    @Test
    fun `po uplywie odliczania mozna wyslac ponownie`() = runTest(dispatcher) {
        val source = CountingResetSource()
        val vm = viewModel(source)
        submit(vm)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, vm.resetCooldownSeconds.value)
        submit(vm)

        assertEquals("po 60 s wysylka ma byc znowu mozliwa, a wyslano lacznie: ${source.sendCount}", 2, source.sendCount)
    }

    @Test
    fun `zamkniecie okna nie zeruje odliczania`() = runTest(dispatcher) {
        val source = CountingResetSource()
        val vm = viewModel(source)
        submit(vm)
        advanceTimeBy(5_000)
        vm.resetPasswordResetState()
        runCurrent()
        submit(vm)

        assertEquals("odliczanie ma przezyc zamkniecie okna, a zostalo: ${vm.resetCooldownSeconds.value}", 55, vm.resetCooldownSeconds.value)
        assertEquals("po ponownym otwarciu okna nie wolno wyslac drugiego maila, a wyslano: ${source.sendCount}", 1, source.sendCount)
    }

    @Test
    fun `nieudana wysylka nie wlacza odliczania`() = runTest(dispatcher) {
        val source = CountingResetSource().apply { failure = FirebaseNetworkException("net") }
        val vm = viewModel(source)
        submit(vm)
        source.failure = null
        submit(vm)

        assertEquals(2, source.sendCount)
        assertEquals(PasswordResetState.Sent(PasswordResetMessages.SENT), vm.passwordReset.value)
    }

    @Test
    fun `pusty adres nie wlacza odliczania`() = runTest(dispatcher) {
        val vm = viewModel(CountingResetSource())
        submit(vm, "  ")

        assertEquals(0, vm.resetCooldownSeconds.value)
    }

    @Test
    fun `adres spoza bazy wlacza to samo odliczanie co adres z bazy`() = runTest(dispatcher) {
        val source = CountingResetSource().apply { failure = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "no user") }
        val vm = viewModel(source)
        submit(vm)

        assertTrue(
            "dla adresu spoza bazy odliczanie musi byc takie samo jak dla adresu z bazy (60 s), a jest: ${vm.resetCooldownSeconds.value}",
            vm.resetCooldownSeconds.value == 60
        )
    }
}