package com.example.stellare

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.ReviewRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.VerificationRepository
import com.example.stellare.data.repository.WalletRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakePostDataSource
import com.example.stellare.fakes.FakeReviewDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeVerificationDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.ui.posts.PostViewModel
import com.example.stellare.ui.profile.ProfileViewModel
import com.example.stellare.ui.verification.DocumentScanCompressionResult
import com.example.stellare.ui.verification.VerificationViewModel
import com.example.stellare.ui.wallet.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClockInjectionTest {

    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = 1_790_000_000_000L
    private val clock = { fixedNow }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun userRepository(users: FakeUserDataSource, uid: String) =
        UserRepository(FakeAuthDataSource(uid), users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users))

    @Test
    fun `doladowanie portfela zapisuje czas z wstrzyknietego zegara`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(UserModel(id = "k1")), balances = mapOf("k1" to 10))
        val wallet = FakeWalletDataSource(users)
        val vm = WalletViewModel(WalletRepository(wallet), userRepository(users, "k1"), clock)

        vm.topUp(50)
        advanceUntilIdle()

        assertEquals(
            "czas doładowania ma pochodzić ze wstrzykniętego zegara, nie z zegara systemu",
            fixedNow,
            wallet.transactions.value.single().timestamp
        )
    }

    @Test
    fun `opinia zapisuje czas z wstrzyknietego zegara`() = runTest(dispatcher) {
        val users = FakeUserDataSource(
            listOf(UserModel(id = "pacjent1", firstName = "Jan", lastName = "Kowalski"), UserModel(id = "psych1"))
        )
        val reviews = FakeReviewDataSource(FakeRatingDataSource())
        reviews.completions += "psych1_pacjent1"
        val vm = ProfileViewModel(
            userRepository(users, "pacjent1"), ReviewRepository(reviews), PostRepository(FakePostDataSource()), clock
        )

        vm.addReview("psych1", 5, "Super")
        advanceUntilIdle()

        assertEquals(
            "czas opinii ma pochodzić ze wstrzykniętego zegara, nie z zegara systemu",
            fixedNow,
            reviews.reviews.value.single().timestamp
        )
    }

    @Test
    fun `ogloszenie zapisuje czas z wstrzyknietego zegara`() = runTest(dispatcher) {
        val users = FakeUserDataSource(
            listOf(UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST, pricePerSession = 100.0))
        )
        val posts = FakePostDataSource()
        val vm = PostViewModel(PostRepository(posts), userRepository(users, "psych1"), clock)

        vm.publish("Konsultacje", "Opis oferty", emptyList())
        advanceUntilIdle()

        assertEquals(
            "czas ogłoszenia ma pochodzić ze wstrzykniętego zegara, nie z zegara systemu",
            fixedNow,
            posts.posts.value.single().createdAt
        )
    }

    @Test
    fun `zgloszenie weryfikacyjne zapisuje czas z wstrzyknietego zegara`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(UserModel(id = "psych1")))
        val verification = FakeVerificationDataSource(users)
        val vm = VerificationViewModel(VerificationRepository(verification), userRepository(users, "psych1"), clock)

        vm.submit(DocumentScanCompressionResult.Success("dane"), "dowod-osobisty")
        advanceUntilIdle()

        assertEquals(
            "czas zgłoszenia ma pochodzić ze wstrzykniętego zegara, nie z zegara systemu",
            fixedNow,
            verification.requests.value.single().submittedAt
        )
    }
}
