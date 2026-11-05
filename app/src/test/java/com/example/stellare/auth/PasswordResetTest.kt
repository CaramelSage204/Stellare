package com.example.stellare.auth

import com.example.stellare.fakes.FakePrivateProfileDataSource
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.UserFacingError
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.auth.PasswordResetMessages
import com.example.stellare.ui.auth.PasswordResetState
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.CompletableDeferred
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

private class ResetAuthSource(
    private val delegate: FakeAuthDataSource = FakeAuthDataSource()
) : AuthDataSource by delegate {
    val sentTo = mutableListOf<String>()
    var behavior: suspend (String) -> Unit = {}

    override suspend fun sendPasswordResetEmail(email: String) {
        sentTo += email
        behavior(email)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordResetTest {

    private val dispatcher = StandardTestDispatcher()
    private val knownEmail = "ewa@example.com"
    private val unknownEmail = "nikt@example.com"

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(source: ResetAuthSource): AuthViewModel {
        val auth = FakeAuthDataSource()
        return AuthViewModel(UserRepository(auth, FakeUserDataSource(), FakeFavoriteDataSource(), FakeRatingDataSource(), FakePrivateProfileDataSource()), source, clock = { dispatcher.scheduler.currentTime })
    }

    private fun sourceWithOnlyKnownAccount() = ResetAuthSource().apply {
        behavior = { email ->
            if (email != knownEmail) throw FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "There is no user record corresponding to this identifier.")
        }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.submit(vm: AuthViewModel, email: String): PasswordResetState {
        vm.sendPasswordReset(email)
        advanceUntilIdle()
        runCurrent()
        return vm.passwordReset.value
    }

    @Test
    fun `na starcie reset hasla jest bezczynny`() = runTest(dispatcher) {
        assertEquals(PasswordResetState.Idle, viewModel(ResetAuthSource()).passwordReset.value)
    }

    @Test
    fun `pusty adres nie wysyla zadania i podaje powod`() = runTest(dispatcher) {
        val source = ResetAuthSource()
        val state = submit(viewModel(source), "   ")

        assertEquals(PasswordResetState.Failed(PasswordResetMessages.EMPTY_EMAIL), state)
        assertTrue("dla pustego adresu nie wolno odpytywac Firebase, a wyslano: ${source.sentTo}", source.sentTo.isEmpty())
    }

    @Test
    fun `w trakcie wysylania stan to Sending a po odpowiedzi Sent`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val source = ResetAuthSource().apply { behavior = { gate.await() } }
        val vm = viewModel(source)

        vm.sendPasswordReset(knownEmail)
        runCurrent()
        assertEquals(PasswordResetState.Sending, vm.passwordReset.value)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(PasswordResetState.Sent(PasswordResetMessages.SENT), vm.passwordReset.value)
    }

    @Test
    fun `adres jest przycinany przed wyslaniem`() = runTest(dispatcher) {
        val source = ResetAuthSource()
        submit(viewModel(source), "  $knownEmail ")

        assertEquals(listOf(knownEmail), source.sentTo)
    }

    @Test
    fun `drugie klikniecie w trakcie wysylania nie wysyla drugiego zadania`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val source = ResetAuthSource().apply { behavior = { gate.await() } }
        val vm = viewModel(source)

        vm.sendPasswordReset(knownEmail)
        runCurrent()
        vm.sendPasswordReset(knownEmail)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, source.sentTo.size)
    }

    @Test
    fun `komunikat po wyslaniu jest identyczny dla adresu z bazy i spoza bazy`() = runTest(dispatcher) {
        val source = sourceWithOnlyKnownAccount()
        val forKnown = submit(viewModel(source), knownEmail)
        val forUnknown = submit(viewModel(source), unknownEmail)

        assertTrue(
            "dla adresu spoza bazy ekran musi pokazac to samo co dla adresu z bazy, a pokazuje: $forUnknown zamiast $forKnown",
            forKnown == forUnknown
        )
        assertTrue("oba stany maja byc Sent, a sa: $forKnown / $forUnknown", forKnown is PasswordResetState.Sent && forUnknown is PasswordResetState.Sent)
    }

    @Test
    fun `tresc po wyslaniu nie twierdzi ze konto istnieje`() {
        assertTrue(
            "komunikat musi byc warunkowy (Jesli konto ... istnieje), a brzmi: ${PasswordResetMessages.SENT}",
            PasswordResetMessages.SENT.startsWith("Jeśli konto") && PasswordResetMessages.SENT.contains("istnieje")
        )
    }

    @Test
    fun `ogolny blad uzytkownika nie istnieje tez daje ten sam komunikat`() = runTest(dispatcher) {
        val source = ResetAuthSource().apply { behavior = { throw FirebaseAuthException("ERROR_USER_NOT_FOUND", "no user") } }
        val state = submit(viewModel(source), unknownEmail)

        assertEquals(PasswordResetState.Sent(PasswordResetMessages.SENT), state)
    }

    @Test
    fun `brak sieci daje komunikat o braku polaczenia`() = runTest(dispatcher) {
        val source = ResetAuthSource().apply { behavior = { throw FirebaseNetworkException("A network error has occurred.") } }

        assertEquals(PasswordResetState.Failed(UserFacingError.NO_CONNECTION), submit(viewModel(source), knownEmail))
    }

    @Test
    fun `zbyt wiele prob daje polski komunikat`() = runTest(dispatcher) {
        val source = ResetAuthSource().apply { behavior = { throw FirebaseTooManyRequestsException("We have blocked all requests from this device.") } }

        assertEquals(PasswordResetState.Failed(UserFacingError.TOO_MANY_ATTEMPTS), submit(viewModel(source), knownEmail))
    }

    @Test
    fun `nieprawidlowy format adresu z Firebase daje komunikat o formacie`() = runTest(dispatcher) {
        val source = ResetAuthSource().apply { behavior = { throw FirebaseAuthException("ERROR_INVALID_EMAIL", "The email address is badly formatted.") } }
        val state = submit(viewModel(source), "to nie jest adres")

        assertEquals(PasswordResetState.Failed("Adres e-mail ma nieprawidłowy format."), state)
    }

    @Test
    fun `nieznany blad nie pokazuje surowego tekstu Firebase`() = runTest(dispatcher) {
        val source = ResetAuthSource().apply { behavior = { throw FirebaseAuthException("ERROR_SOMETHING_NEW", "An internal Firebase problem occurred in English.") } }
        val state = submit(viewModel(source), knownEmail)

        assertEquals(PasswordResetState.Failed(PasswordResetMessages.FAILED), state)
    }

    @Test
    fun `wyjatek spoza Firebase tez nie wycieka na ekran`() = runTest(dispatcher) {
        val source = ResetAuthSource().apply { behavior = { throw IllegalStateException("java.lang.IllegalStateException: boom") } }
        val state = submit(viewModel(source), knownEmail)

        assertEquals(PasswordResetState.Failed(PasswordResetMessages.FAILED), state)
    }

    @Test
    fun `blad resetu nie trafia do wspolnego bledu logowania`() = runTest(dispatcher) {
        val source = ResetAuthSource().apply { behavior = { throw FirebaseNetworkException("net") } }
        val vm = viewModel(source)
        submit(vm, knownEmail)

        assertNull("blad resetu hasla ma zyc we wlasnym stanie, a authError ma: ${vm.authError.value}", vm.authError.value)
    }

    @Test
    fun `czyszczenie bledu logowania nie kasuje komunikatu resetu`() = runTest(dispatcher) {
        val vm = viewModel(sourceWithOnlyKnownAccount())
        val before = submit(vm, knownEmail)
        vm.clearAuthError()

        assertEquals(before, vm.passwordReset.value)
    }

    @Test
    fun `po bledzie mozna sprobowac ponownie i dostac Sent`() = runTest(dispatcher) {
        val source = ResetAuthSource()
        var fail = true
        source.behavior = { if (fail) throw FirebaseNetworkException("net") }
        val vm = viewModel(source)
        submit(vm, knownEmail)
        fail = false

        assertEquals(PasswordResetState.Sent(PasswordResetMessages.SENT), submit(vm, knownEmail))
    }

    @Test
    fun `zamkniecie okna przywraca stan bezczynny`() = runTest(dispatcher) {
        val vm = viewModel(ResetAuthSource())
        submit(vm, knownEmail)
        vm.resetPasswordResetState()

        assertEquals(PasswordResetState.Idle, vm.passwordReset.value)
    }
}