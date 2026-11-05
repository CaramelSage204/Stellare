package com.example.stellare.auth

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.remote.PhoneCodeRequest
import com.example.stellare.data.model.PrivateProfile
import com.example.stellare.data.remote.PrivateProfileDataSource
import com.example.stellare.data.remote.UserDataSource
import com.example.stellare.fakes.FakePrivateProfileDataSource
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.auth.PhoneVerificationMessages
import com.example.stellare.ui.auth.PhoneVerificationState
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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

private const val NUMBER = "+48501234567"
private const val OTHER_NUMBER = "+48600700800"
private const val UID = "u1"

private class PhoneAuthSource(
    private val delegate: FakeAuthDataSource = FakeAuthDataSource(initialUserId = UID)
) : AuthDataSource by delegate {
    var linkedNumber: String? = null
    var requestResult: Result<PhoneCodeRequest?> = Result.success(PhoneCodeRequest.CodeSent("sesja-1"))
    var confirmResult: Result<String?> = Result.success(NUMBER)
    var requestDelayMs = 0L
    val requested = mutableListOf<String>()
    val confirmed = mutableListOf<Pair<String, String>>()

    override fun getCurrentUserPhoneNumberOnce(): String? = linkedNumber

    override suspend fun requestPhoneCode(phoneNumber: String): PhoneCodeRequest? {
        requested += phoneNumber
        if (requestDelayMs > 0) delay(requestDelayMs)
        return requestResult.getOrThrow()
    }

    override suspend fun confirmPhoneCode(verificationId: String, code: String): String? {
        confirmed += verificationId to code
        return confirmResult.getOrThrow()?.also { linkedNumber = it }
    }
}

private class PhonePrivateSource(private val base: FakePrivateProfileDataSource) : PrivateProfileDataSource by base {
    var failure: Exception? = null
    val saved = mutableListOf<Pair<String, String>>()

    override suspend fun savePhone(uid: String, phone: String) {
        failure?.let { throw it }
        saved += uid to phone
        base.savePhone(uid, phone)
    }
}

private class PhoneUserSource(
    storedPhone: String = "",
    private val base: FakeUserDataSource = FakeUserDataSource(listOf(UserModel(id = UID, firstName = "Anna")))
) : UserDataSource by base {
    val privateSource = PhonePrivateSource(base.privateProfiles)
    var failure: Exception? by privateSource::failure
    val saved get() = privateSource.saved

    init {
        base.privateProfiles.profiles.value = base.privateProfiles.profiles.value + (UID to PrivateProfile(phone = storedPhone))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneVerificationTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(source: PhoneAuthSource, users: PhoneUserSource = PhoneUserSource()): AuthViewModel =
        AuthViewModel(
            UserRepository(FakeAuthDataSource(initialUserId = UID), users, FakeFavoriteDataSource(), FakeRatingDataSource(), users.privateSource),
            source,
            clock = { dispatcher.scheduler.currentTime }
        )

    private fun TestScope.run(action: () -> Unit) {
        action()
        runCurrent()
    }

    private fun TestScope.openAndSend(vm: AuthViewModel, input: String = NUMBER) {
        run { vm.openPhoneVerification() }
        run { vm.requestPhoneCode(input) }
    }

    private fun TestScope.sendAndConfirm(vm: AuthViewModel, code: String = "123456") {
        openAndSend(vm)
        run { vm.confirmPhoneCode(code) }
    }

    @Test
    fun `otwarcie bez numeru na koncie pokazuje pole numeru`() = runTest(dispatcher) {
        val vm = viewModel(PhoneAuthSource())
        run { vm.openPhoneVerification() }

        assertEquals(PhoneVerificationState.EnteringNumber(), vm.phoneVerification.value)
    }

    @Test
    fun `otwarcie z numerem powiazanym z kontem pokazuje potwierdzenie tego numeru`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply { linkedNumber = NUMBER }
        val users = PhoneUserSource(storedPhone = NUMBER)
        val vm = viewModel(source, users)
        run { vm.openPhoneVerification() }

        assertEquals(PhoneVerificationState.Verified(NUMBER), vm.phoneVerification.value)
        assertTrue("profil ma juz ten numer, wiec nie ma czego dopisywac: ${users.saved}", users.saved.isEmpty())
    }

    @Test
    fun `otwarcie z numerem na koncie, ktorego brak w profilu, dopisuje go do profilu`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply { linkedNumber = NUMBER }
        val users = PhoneUserSource()
        val vm = viewModel(source, users)
        run { vm.openPhoneVerification() }

        assertEquals(listOf(UID to NUMBER), users.saved)
        assertEquals(PhoneVerificationState.Verified(NUMBER), vm.phoneVerification.value)
    }

    @Test
    fun `numer z samego dokumentu nie daje potwierdzenia, bo konto go nie ma`() = runTest(dispatcher) {
        val users = PhoneUserSource(storedPhone = "500600700")
        val vm = viewModel(PhoneAuthSource(), users)
        run { vm.openPhoneVerification() }

        assertEquals(
            "dawny numer z profilu nie jest potwierdzony przez konto, wiec ekran nie moze twierdzic inaczej",
            PhoneVerificationState.EnteringNumber(),
            vm.phoneVerification.value
        )
    }

    @Test
    fun `blad dopisania numeru do profilu przy otwarciu zostawia znacznik zamiast udawac sukces`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply { linkedNumber = NUMBER }
        val users = PhoneUserSource().apply { failure = FirebaseNetworkException("net") }
        val vm = viewModel(source, users)
        run { vm.openPhoneVerification() }

        assertEquals(PhoneVerificationState.Verified(NUMBER, profileSaveFailed = true), vm.phoneVerification.value)
    }

    @Test
    fun `ponowna proba zapisu do profilu czyści znacznik po powodzeniu`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply { linkedNumber = NUMBER }
        val users = PhoneUserSource().apply { failure = FirebaseNetworkException("net") }
        val vm = viewModel(source, users)
        run { vm.openPhoneVerification() }
        users.failure = null
        run { vm.retrySavingPhoneToProfile() }

        assertEquals(PhoneVerificationState.Verified(NUMBER), vm.phoneVerification.value)
        assertEquals(listOf(UID to NUMBER), users.saved)
    }

    @Test
    fun `nieprawidlowy numer nie dociera do zrodla`() = runTest(dispatcher) {
        val source = PhoneAuthSource()
        val vm = viewModel(source)
        openAndSend(vm, "123")

        assertTrue("do Firebase nie wolno wyslac '123', a wyslano: ${source.requested}", source.requested.isEmpty())
        assertEquals(PhoneVerificationState.EnteringNumber(PhoneVerificationMessages.INVALID_NUMBER), vm.phoneVerification.value)
    }

    @Test
    fun `numer krajowy jest wysylany w formacie miedzynarodowym`() = runTest(dispatcher) {
        val source = PhoneAuthSource()
        val vm = viewModel(source)
        openAndSend(vm, "501 234 567")

        assertEquals(listOf(NUMBER), source.requested)
    }

    @Test
    fun `udana wysylka przechodzi do wpisania kodu i nie twierdzi potwierdzenia`() = runTest(dispatcher) {
        val vm = viewModel(PhoneAuthSource())
        openAndSend(vm)

        assertEquals(PhoneVerificationState.EnteringCode(NUMBER), vm.phoneVerification.value)
    }

    @Test
    fun `nieudana wysylka wraca do pola numeru z komunikatem`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply { requestResult = Result.failure(FirebaseNetworkException("net")) }
        val vm = viewModel(source)
        openAndSend(vm)

        assertEquals(PhoneVerificationState.EnteringNumber("Brak połączenia z serwerem. Sprawdź internet i spróbuj ponownie."), vm.phoneVerification.value)
    }

    @Test
    fun `zrodlo bez obslugi wysylki daje blad a nie sukces`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply { requestResult = Result.success(null) }
        val vm = viewModel(source)
        openAndSend(vm)

        assertEquals(PhoneVerificationState.EnteringNumber(PhoneVerificationMessages.SEND_FAILED), vm.phoneVerification.value)
    }

    @Test
    fun `wysylka z natychmiastowym potwierdzeniem daje potwierdzenie i zapis do profilu`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply {
            requestResult = Result.success(PhoneCodeRequest.AlreadyLinked(NUMBER))
            confirmResult = Result.success(NUMBER)
        }
        val users = PhoneUserSource()
        val vm = viewModel(source, users)
        openAndSend(vm)

        assertEquals(PhoneVerificationState.Verified(NUMBER), vm.phoneVerification.value)
        assertEquals(listOf(UID to NUMBER), users.saved)
    }

    @Test
    fun `w trakcie wysylki druga wysylka nie dociera do zrodla`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply { requestDelayMs = 2_000 }
        val vm = viewModel(source)
        run { vm.openPhoneVerification() }
        run { vm.requestPhoneCode(NUMBER) }
        run { vm.requestPhoneCode(NUMBER) }

        assertEquals("podwojne dotkniecie nie moze wyslac dwoch SMS-ow: ${source.requested}", 1, source.requested.size)
        assertEquals(PhoneVerificationState.SendingCode, vm.phoneVerification.value)
    }

    @Test
    fun `po wysylce odliczanie startuje od 60 sekund`() = runTest(dispatcher) {
        val vm = viewModel(PhoneAuthSource())
        openAndSend(vm)

        assertEquals("po wysylce kolejny SMS ma byc zablokowany na 60 s, a pozostalo: ${vm.phoneResendCooldownSeconds.value}", 60, vm.phoneResendCooldownSeconds.value)
    }

    @Test
    fun `w trakcie odliczania druga wysylka nie dociera do zrodla a po nim tak`() = runTest(dispatcher) {
        val source = PhoneAuthSource()
        val vm = viewModel(source)
        openAndSend(vm)
        advanceTimeBy(30_000)
        run { vm.requestPhoneCode(NUMBER) }

        assertEquals("po 30 s SMS nie moze wyjsc drugi raz: ${source.requested}", 1, source.requested.size)
        assertEquals(30, vm.phoneResendCooldownSeconds.value)

        advanceTimeBy(30_000)
        runCurrent()
        run { vm.requestPhoneCode(NUMBER) }

        assertEquals("po 60 s wysylka ma byc znowu mozliwa: ${source.requested}", 2, source.requested.size)
    }

    @Test
    fun `zmiana numeru nie omija odliczania`() = runTest(dispatcher) {
        val source = PhoneAuthSource()
        val vm = viewModel(source)
        openAndSend(vm)
        run { vm.changePhoneNumber() }
        run { vm.requestPhoneCode(OTHER_NUMBER) }

        assertEquals("inny numer nie moze wyslac SMS-a w trakcie odliczania: ${source.requested}", listOf(NUMBER), source.requested)
        assertTrue(vm.phoneVerification.value is PhoneVerificationState.EnteringNumber)
    }

    @Test
    fun `nieudana wysylka nie wlacza odliczania`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply { requestResult = Result.failure(FirebaseNetworkException("net")) }
        val vm = viewModel(source)
        openAndSend(vm)

        assertEquals(0, vm.phoneResendCooldownSeconds.value)
    }

    @Test
    fun `kod odrzucony zostaje we wpisywaniu kodu i nie potwierdza numeru`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply {
            confirmResult = Result.failure(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_VERIFICATION_CODE", "x"))
        }
        val users = PhoneUserSource()
        val vm = viewModel(source, users)
        sendAndConfirm(vm)

        assertEquals(PhoneVerificationState.EnteringCode(NUMBER, PhoneVerificationMessages.WRONG_CODE), vm.phoneVerification.value)
        assertTrue("odrzucony kod nie moze zmienic profilu: ${users.saved}", users.saved.isEmpty())
    }

    @Test
    fun `kod przyjety potwierdza numer i zapisuje go w profilu`() = runTest(dispatcher) {
        val users = PhoneUserSource()
        val vm = viewModel(PhoneAuthSource(), users)
        sendAndConfirm(vm)

        assertEquals(PhoneVerificationState.Verified(NUMBER), vm.phoneVerification.value)
        assertEquals(listOf(UID to NUMBER), users.saved)
    }

    @Test
    fun `potwierdzony jest numer zwrocony przez konto a nie ten wpisany`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply { confirmResult = Result.success(OTHER_NUMBER) }
        val users = PhoneUserSource()
        val vm = viewModel(source, users)
        sendAndConfirm(vm)

        assertEquals(PhoneVerificationState.Verified(OTHER_NUMBER), vm.phoneVerification.value)
        assertEquals("do profilu ma trafic numer z konta: ${users.saved}", listOf(UID to OTHER_NUMBER), users.saved)
    }

    @Test
    fun `kod trafia do zrodla z identyfikatorem sesji wysylki`() = runTest(dispatcher) {
        val source = PhoneAuthSource()
        val vm = viewModel(source)
        sendAndConfirm(vm, "654321")

        assertEquals(listOf("sesja-1" to "654321"), source.confirmed)
    }

    @Test
    fun `kod niebedacy szescioma cyframi nie dociera do zrodla`() = runTest(dispatcher) {
        val source = PhoneAuthSource()
        val vm = viewModel(source)
        openAndSend(vm)
        listOf("", "12345", "1234567", "12ab56", "１２３４５６").forEach { run { vm.confirmPhoneCode(it) } }

        assertTrue("do Firebase nie wolno wyslac blednego kodu: ${source.confirmed}", source.confirmed.isEmpty())
        assertEquals(PhoneVerificationState.EnteringCode(NUMBER, PhoneVerificationMessages.INVALID_CODE_FORMAT), vm.phoneVerification.value)
    }

    @Test
    fun `kod ze spacjami wokol jest przycinany`() = runTest(dispatcher) {
        val source = PhoneAuthSource()
        val vm = viewModel(source)
        sendAndConfirm(vm, "  123456 ")

        assertEquals(listOf("sesja-1" to "123456"), source.confirmed)
    }

    @Test
    fun `numer zajety przez inne konto wraca do pola numeru z komunikatem o innym koncie`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply {
            confirmResult = Result.failure(FirebaseAuthUserCollisionException("ERROR_CREDENTIAL_ALREADY_IN_USE", "x"))
        }
        val vm = viewModel(source)
        sendAndConfirm(vm)

        assertEquals(PhoneVerificationState.EnteringNumber(PhoneVerificationMessages.NUMBER_IN_USE), vm.phoneVerification.value)
    }

    @Test
    fun `kod wygasly zostaje we wpisywaniu kodu z odpowiednim komunikatem`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply {
            confirmResult = Result.failure(FirebaseAuthInvalidCredentialsException("ERROR_SESSION_EXPIRED", "x"))
        }
        val vm = viewModel(source)
        sendAndConfirm(vm)

        assertEquals(PhoneVerificationState.EnteringCode(NUMBER, PhoneVerificationMessages.CODE_EXPIRED), vm.phoneVerification.value)
    }

    @Test
    fun `potwierdzenie bez wyslanego kodu nie dociera do zrodla`() = runTest(dispatcher) {
        val source = PhoneAuthSource()
        val vm = viewModel(source)
        run { vm.openPhoneVerification() }
        run { vm.confirmPhoneCode("123456") }

        assertTrue(source.confirmed.isEmpty())
        assertEquals(PhoneVerificationState.EnteringNumber(), vm.phoneVerification.value)
    }

    @Test
    fun `zrodlo bez obslugi potwierdzenia nie daje potwierdzenia`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply { confirmResult = Result.success(null) }
        val vm = viewModel(source)
        sendAndConfirm(vm)

        assertEquals(PhoneVerificationState.EnteringCode(NUMBER, PhoneVerificationMessages.CONFIRM_FAILED), vm.phoneVerification.value)
    }

    @Test
    fun `blad zapisu do profilu po powiazaniu zostawia potwierdzenie ze znacznikiem`() = runTest(dispatcher) {
        val users = PhoneUserSource().apply { failure = FirebaseNetworkException("net") }
        val vm = viewModel(PhoneAuthSource(), users)
        sendAndConfirm(vm)

        assertEquals(PhoneVerificationState.Verified(NUMBER, profileSaveFailed = true), vm.phoneVerification.value)
    }

    @Test
    fun `podwojne potwierdzenie nie dociera do zrodla dwa razy`() = runTest(dispatcher) {
        val source = PhoneAuthSource()
        val vm = viewModel(source)
        openAndSend(vm)
        vm.confirmPhoneCode("123456")
        vm.confirmPhoneCode("123456")
        runCurrent()

        assertEquals(1, source.confirmed.size)
    }

    @Test
    fun `bledy weryfikacji telefonu nie trafiaja do wspolnego authError`() = runTest(dispatcher) {
        val source = PhoneAuthSource().apply {
            confirmResult = Result.failure(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_VERIFICATION_CODE", "x"))
        }
        val vm = viewModel(source)
        sendAndConfirm(vm)

        assertNull(vm.authError.value)
        vm.clearAuthError()
        assertEquals(
            "czyszczenie bledu logowania nie moze kasowac komunikatu o kodzie",
            PhoneVerificationState.EnteringCode(NUMBER, PhoneVerificationMessages.WRONG_CODE),
            vm.phoneVerification.value
        )
    }

    @Test
    fun `zamkniecie ekranu w trakcie wpisywania kodu czysci stan i nie zeruje odliczania`() = runTest(dispatcher) {
        val vm = viewModel(PhoneAuthSource())
        openAndSend(vm)
        run { vm.closePhoneVerification() }

        assertEquals(PhoneVerificationState.EnteringNumber(), vm.phoneVerification.value)
        assertEquals(60, vm.phoneResendCooldownSeconds.value)
    }

    @Test
    fun `wylogowanie czysci stan weryfikacji`() = runTest(dispatcher) {
        val source = PhoneAuthSource()
        val vm = viewModel(source)
        sendAndConfirm(vm)
        run { vm.logout() }

        assertEquals(PhoneVerificationState.EnteringNumber(), vm.phoneVerification.value)
        assertNull(vm.phoneVerificationOffer.value)
    }
}
