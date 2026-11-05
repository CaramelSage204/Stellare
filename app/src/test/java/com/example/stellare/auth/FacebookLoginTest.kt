package com.example.stellare.auth

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.UserFacingError
import com.example.stellare.ui.auth.AuthStatus
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.auth.FacebookLogin
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWebException
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FacebookSource(
    private val delegate: FakeAuthDataSource = FakeAuthDataSource()
) : AuthDataSource by delegate {
    var result: Result<String?> = Result.success(null)
    var gate: CompletableDeferred<Unit>? = null
    var calls = 0

    override suspend fun signInWithFacebook(): String? {
        calls++
        gate?.await()
        return result.getOrThrow()?.also { delegate.userId.value = it }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FacebookLoginTest {

    private val dispatcher = StandardTestDispatcher()
    private val genericFailure = "Błąd logowania Facebook: nie udało się zalogować. Spróbuj ponownie."

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(source: FacebookSource, users: FakeUserDataSource = FakeUserDataSource()) =
        AuthViewModel(UserRepository(source, users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)), source)

    @Test
    fun `przycisk Facebooka jest ukryty gdy nie ma identyfikatora aplikacji`() {
        assertFalse("brak identyfikatora (null) ma ukryć przycisk", FacebookLogin.isAvailable(null))
        assertFalse("pusty identyfikator ma ukryć przycisk", FacebookLogin.isAvailable(""))
        assertFalse("same spacje mają ukryć przycisk", FacebookLogin.isAvailable("   "))
    }

    @Test
    fun `przycisk Facebooka jest ukryty dla wartosci zastepczej z pliku przykladowego`() {
        assertFalse("MY_FACEBOOK_APP_ID to wartość zastępcza, nie identyfikator", FacebookLogin.isAvailable("MY_FACEBOOK_APP_ID"))
        assertFalse("identyfikator z literami nie jest identyfikatorem Facebooka", FacebookLogin.isAvailable("12345abc"))
        assertFalse("identyfikator z cudzysłowem nie jest identyfikatorem Facebooka", FacebookLogin.isAvailable("\"1234567890\""))
    }

    @Test
    fun `przycisk Facebooka jest widoczny dla liczbowego identyfikatora`() {
        assertTrue("liczbowy identyfikator ma pokazać przycisk", FacebookLogin.isAvailable("1234567890123456"))
        assertTrue("identyfikator otoczony spacjami jest poprawny", FacebookLogin.isAvailable(" 1234567890123456 "))
    }

    @Test
    fun `zrodlo bez obslugi Facebooka zwraca null a nie udaje sukcesu`() = runTest(dispatcher) {
        val source = object : AuthDataSource by FakeAuthDataSource() {}
        assertNull(source.signInWithFacebook())
    }

    @Test
    fun `pierwsze logowanie przez Facebooka bez dokumentu prowadzi do uzupelnienia profilu`() = runTest(dispatcher) {
        val source = FacebookSource().apply { result = Result.success("fb1") }
        val vm = viewModel(source)
        backgroundScope.launch { vm.authStatus.collect {} }
        advanceUntilIdle()
        assertEquals(AuthStatus.LOGGED_OUT, vm.authStatus.value)

        vm.signInWithFacebook()
        advanceUntilIdle()
        runCurrent()

        assertEquals(AuthStatus.PROFILE_INCOMPLETE, vm.authStatus.value)
        assertNull(vm.authError.value)
    }

    @Test
    fun `logowanie przez Facebooka istniejacego uzytkownika wpuszcza do aplikacji`() = runTest(dispatcher) {
        val source = FacebookSource().apply { result = Result.success("u1") }
        val vm = viewModel(source, FakeUserDataSource(listOf(UserModel(id = "u1"))))
        backgroundScope.launch { vm.authStatus.collect {} }
        advanceUntilIdle()

        vm.signInWithFacebook()
        advanceUntilIdle()
        runCurrent()

        assertEquals(AuthStatus.LOGGED_IN, vm.authStatus.value)
    }

    @Test
    fun `anulowanie logowania przez Facebooka daje polskie zdanie`() = runTest(dispatcher) {
        val source = FacebookSource().apply {
            result = Result.failure(FirebaseAuthWebException("ERROR_WEB_CONTEXT_CANCELED", "The web operation was canceled by the user."))
        }
        val vm = viewModel(source)

        vm.signInWithFacebook()
        advanceUntilIdle()

        assertEquals("Błąd logowania Facebook: ${UserFacingError.FACEBOOK_SIGN_IN_CANCELLED}", vm.authError.value)
    }

    @Test
    fun `surowy angielski blad przy logowaniu przez Facebooka nie trafia do uzytkownika`() = runTest(dispatcher) {
        val source = FacebookSource().apply { result = Result.failure(IllegalStateException("Raw English failure")) }
        val vm = viewModel(source)

        vm.signInWithFacebook()
        advanceUntilIdle()

        assertEquals(genericFailure, vm.authError.value)
    }

    @Test
    fun `brak sieci przy logowaniu przez Facebooka daje komunikat o polaczeniu`() = runTest(dispatcher) {
        val source = FacebookSource().apply { result = Result.failure(FirebaseNetworkException("A network error")) }
        val vm = viewModel(source)

        vm.signInWithFacebook()
        advanceUntilIdle()

        assertEquals("Błąd logowania Facebook: ${UserFacingError.NO_CONNECTION}", vm.authError.value)
    }

    @Test
    fun `konto istniejace z innym sposobem logowania daje polskie zdanie`() = runTest(dispatcher) {
        val source = FacebookSource().apply {
            result = Result.failure(FirebaseAuthUserCollisionException("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "An account already exists"))
        }
        val vm = viewModel(source)

        vm.signInWithFacebook()
        advanceUntilIdle()

        assertEquals("Błąd logowania Facebook: ${UserFacingError.ACCOUNT_EXISTS_WITH_OTHER_METHOD}", vm.authError.value)
    }

    @Test
    fun `wylaczony dostawca w konsoli daje komunikat o niedostepnosci a nie kod`() {
        val blad = FirebaseAuthWebException("ERROR_OPERATION_NOT_ALLOWED", "This operation is not allowed.")
        assertEquals(UserFacingError.SIGN_IN_METHOD_UNAVAILABLE, UserFacingError.authMessage(blad, "x"))
    }

    @Test
    fun `null ze zrodla czyli brak aktywnosci daje blad a nie cichy sukces`() = runTest(dispatcher) {
        val vm = viewModel(FacebookSource())

        vm.signInWithFacebook()
        advanceUntilIdle()

        assertEquals(genericFailure, vm.authError.value)
    }

    @Test
    fun `drugie dotkniecie w trakcie logowania nie uruchamia drugiego okna Facebooka`() = runTest(dispatcher) {
        val source = FacebookSource().apply {
            gate = CompletableDeferred()
            result = Result.success("fb1")
        }
        val vm = viewModel(source)

        vm.signInWithFacebook()
        runCurrent()
        vm.signInWithFacebook()
        runCurrent()
        source.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals("logowanie ma ruszyć raz mimo dwóch dotknięć", 1, source.calls)
    }

    @Test
    fun `po zakonczonym logowaniu mozna ponowic probe`() = runTest(dispatcher) {
        val source = FacebookSource().apply { result = Result.failure(IllegalStateException("x")) }
        val vm = viewModel(source)

        vm.signInWithFacebook()
        advanceUntilIdle()
        vm.signInWithFacebook()
        advanceUntilIdle()

        assertEquals(2, source.calls)
    }

    @Test
    fun `konto Facebooka bez adresu e-mail przechodzi uzupelnienie profilu i wpuszcza do aplikacji`() = runTest(dispatcher) {
        val source = FacebookSource().apply { result = Result.success("fb1") }
        val users = FakeUserDataSource()
        val vm = viewModel(source, users)
        backgroundScope.launch { vm.authStatus.collect {} }
        advanceUntilIdle()

        vm.signInWithFacebook()
        advanceUntilIdle()
        runCurrent()
        assertEquals(AuthStatus.PROFILE_INCOMPLETE, vm.authStatus.value)
        assertNull("źródło nie zna adresu e-mail", source.getCurrentUserEmailOnce())

        vm.completeProfile(UserRole.PATIENT, "Ewa", "Nowak", "41", "Kobieta", "", "")
        advanceUntilIdle()
        runCurrent()

        assertNull("brak adresu nie może blokować uzupełnienia", vm.authError.value)
        assertEquals("", users.privateProfiles.profiles.value.getValue("fb1").email)
        assertEquals(AuthStatus.LOGGED_IN, vm.authStatus.value)
    }
}
