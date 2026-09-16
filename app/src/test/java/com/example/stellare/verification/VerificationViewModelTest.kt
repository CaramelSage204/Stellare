package com.example.stellare.verification

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.VerificationRequestModel
import com.example.stellare.data.model.VerificationStatus
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.VerificationRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeVerificationDataSource
import com.example.stellare.ui.verification.DocumentScanCompressionResult
import com.example.stellare.ui.verification.FIRESTORE_DOCUMENT_SAFE_LIMIT_BYTES
import com.example.stellare.ui.verification.VerificationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun zbuduj(
        zalogowanyUid: String? = "psych1",
        uzytkownicy: List<UserModel> = listOf(
            UserModel(id = "psych1", isVerified = false)
        )
    ): Triple<VerificationViewModel, FakeVerificationDataSource, FakeUserDataSource> {
        val userDataSource = FakeUserDataSource(uzytkownicy)
        val verificationDataSource = FakeVerificationDataSource(userDataSource)
        val userRepository = UserRepository(
            FakeAuthDataSource(zalogowanyUid),
            userDataSource,
            FakeFavoriteDataSource()
        )
        val vm = VerificationViewModel(
            VerificationRepository(verificationDataSource),
            userRepository
        )
        return Triple(vm, verificationDataSource, userDataSource)
    }

    @Test
    fun `submit z poprawnym zdjeciem zwraca true i zapisuje zgloszenie`() = runTest(dispatcher) {
        val (vm, verificationDataSource, _) = zbuduj()
        val subskrypcja = launch { vm.myRequest.collect {} }
        advanceUntilIdle()

        val result = vm.submit(DocumentScanCompressionResult.Success("dane-obrazu-base64"), "dowod-osobisty")

        assertTrue(result)
        assertEquals(1, verificationDataSource.requests.value.size)
        val zapisane = verificationDataSource.requests.value.first()
        assertEquals("psych1", zapisane.uid)
        assertEquals("dane-obrazu-base64", zapisane.docImageBase64)
        assertEquals("dowod-osobisty", zapisane.docType)
        assertEquals(VerificationStatus.PENDING, zapisane.status)
        assertNull(vm.submitError.value)
        subskrypcja.cancel()
    }

    @Test
    fun `submit bez wybranego zdjecia zwraca false ustawia komunikat i nic nie zapisuje`() = runTest(dispatcher) {
        val (vm, verificationDataSource, _) = zbuduj()

        val result = vm.submit(null, "dowod-osobisty")

        assertFalse(result)
        assertTrue(verificationDataSource.requests.value.isEmpty())
        assertEquals("Najpierw wybierz zdjęcie dokumentu.", vm.submitError.value)
    }

    @Test
    fun `submit z pustym lancuchem zamiast zdjecia zwraca false ustawia komunikat i nic nie zapisuje`() = runTest(dispatcher) {
        val (vm, verificationDataSource, _) = zbuduj()

        val result = vm.submit(DocumentScanCompressionResult.Success(""), "dowod-osobisty")

        assertFalse(result)
        assertTrue(verificationDataSource.requests.value.isEmpty())
        assertEquals("Najpierw wybierz zdjęcie dokumentu.", vm.submitError.value)
    }

    @Test
    fun `submit z za duzym zdjeciem zwraca false ustawia komunikat i nic nie zapisuje`() = runTest(dispatcher) {
        val (vm, verificationDataSource, _) = zbuduj()
        val zaDuzeZdjecie = "a".repeat(FIRESTORE_DOCUMENT_SAFE_LIMIT_BYTES + 1)

        val result = vm.submit(DocumentScanCompressionResult.Success(zaDuzeZdjecie), "dowod-osobisty")

        assertFalse(result)
        assertTrue(verificationDataSource.requests.value.isEmpty())
        assertNotNull(vm.submitError.value)
        assertTrue(vm.submitError.value!!.startsWith("Zdjęcie jest zbyt duże"))
    }

    @Test
    fun `submit z UnreadableImage zwraca false i ustawia komunikat o nieudanym odczycie a nie o rozmiarze`() = runTest(dispatcher) {
        val (vm, verificationDataSource, _) = zbuduj()

        val result = vm.submit(DocumentScanCompressionResult.UnreadableImage, "dowod-osobisty")

        assertFalse(result)
        assertTrue(verificationDataSource.requests.value.isEmpty())
        assertEquals(
            "Nie udało się odczytać zdjęcia. Wybierz inny plik lub zrób nowe zdjęcie.",
            vm.submitError.value
        )
    }

    @Test
    fun `submit z TooLarge zwraca false ustawia komunikat o rozmiarze i nic nie zapisuje`() = runTest(dispatcher) {
        val (vm, verificationDataSource, _) = zbuduj()

        val result = vm.submit(DocumentScanCompressionResult.TooLarge, "dowod-osobisty")

        assertFalse(result)
        assertTrue(verificationDataSource.requests.value.isEmpty())
        assertNotNull(vm.submitError.value)
        assertTrue(vm.submitError.value!!.startsWith("Zdjęcie jest zbyt duże"))
    }

    @Test
    fun `decide z approved true ustawia odznake isVerified`() = runTest(dispatcher) {
        val (vm, verificationDataSource, userDataSource) = zbuduj(
            zalogowanyUid = "admin1",
            uzytkownicy = listOf(
                UserModel(id = "admin1", isAdmin = true),
                UserModel(id = "psych1", isVerified = false)
            )
        )
        verificationDataSource.requests.value = listOf(
            VerificationRequestModel(uid = "psych1", docImageBase64 = "x", docType = "dowod", status = VerificationStatus.PENDING)
        )

        val result = vm.decide("psych1", approved = true, note = "")

        assertTrue(result)
        assertTrue(userDataSource.users.value.first { it.id == "psych1" }.isVerified)
        assertEquals(VerificationStatus.APPROVED, verificationDataSource.requests.value.first { it.uid == "psych1" }.status)
    }

    @Test
    fun `decide z approved false zapisuje notatke i nie ustawia odznaki`() = runTest(dispatcher) {
        val (vm, verificationDataSource, userDataSource) = zbuduj(
            zalogowanyUid = "admin1",
            uzytkownicy = listOf(
                UserModel(id = "admin1", isAdmin = true),
                UserModel(id = "psych1", isVerified = false)
            )
        )
        verificationDataSource.requests.value = listOf(
            VerificationRequestModel(uid = "psych1", docImageBase64 = "x", docType = "dowod", status = VerificationStatus.PENDING)
        )

        val result = vm.decide("psych1", approved = false, note = "Zdjęcie nieczytelne")

        assertTrue(result)
        assertFalse(userDataSource.users.value.first { it.id == "psych1" }.isVerified)
        val zaktualizowane = verificationDataSource.requests.value.first { it.uid == "psych1" }
        assertEquals(VerificationStatus.REJECTED, zaktualizowane.status)
        assertEquals("Zdjęcie nieczytelne", zaktualizowane.reviewNote)
    }

    @Test
    fun `decide nie rusza przed zalogowaniem`() = runTest(dispatcher) {
        val (vm, verificationDataSource, userDataSource) = zbuduj(
            zalogowanyUid = null,
            uzytkownicy = listOf(UserModel(id = "psych1", isVerified = false))
        )
        verificationDataSource.requests.value = listOf(
            VerificationRequestModel(uid = "psych1", docImageBase64 = "x", docType = "dowod", status = VerificationStatus.PENDING)
        )

        val result = vm.decide("psych1", approved = true, note = "")

        assertFalse(result)
        assertFalse(userDataSource.users.value.first { it.id == "psych1" }.isVerified)
        assertEquals(VerificationStatus.PENDING, verificationDataSource.requests.value.first { it.uid == "psych1" }.status)
    }

    @Test
    fun `decide z approved false i pusta notatka zwraca false ustawia komunikat i nic nie zapisuje`() = runTest(dispatcher) {
        val (vm, verificationDataSource, _) = zbuduj(
            zalogowanyUid = "admin1",
            uzytkownicy = listOf(
                UserModel(id = "admin1", isAdmin = true),
                UserModel(id = "psych1", isVerified = false)
            )
        )
        verificationDataSource.requests.value = listOf(
            VerificationRequestModel(uid = "psych1", docImageBase64 = "x", docType = "dowod", status = VerificationStatus.PENDING)
        )

        val result = vm.decide("psych1", approved = false, note = "   ")

        assertFalse(result)
        assertEquals(VerificationStatus.PENDING, verificationDataSource.requests.value.first { it.uid == "psych1" }.status)
        assertEquals("Podaj powód odrzucenia zgłoszenia.", vm.submitError.value)
    }

    @Test
    fun `myRequest nie jest ladowany przed zalogowaniem i laduje sie po zalogowaniu`() = runTest(dispatcher) {
        val userDataSource = FakeUserDataSource(listOf(UserModel(id = "psych1")))
        val verificationDataSource = FakeVerificationDataSource(userDataSource)
        verificationDataSource.requests.value = listOf(
            VerificationRequestModel(uid = "psych1", docImageBase64 = "x", docType = "dowod", status = VerificationStatus.PENDING)
        )
        val authDataSource = FakeAuthDataSource(initialUserId = null)
        val userRepository = UserRepository(authDataSource, userDataSource, FakeFavoriteDataSource())
        val vm = VerificationViewModel(VerificationRepository(verificationDataSource), userRepository)

        val subskrypcja = launch { vm.myRequest.collect {} }
        advanceUntilIdle()

        assertNull(vm.myRequest.value)

        authDataSource.userId.value = "psych1"
        advanceUntilIdle()

        assertEquals("psych1", vm.myRequest.value?.uid)
        subskrypcja.cancel()
    }
}
