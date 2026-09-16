package com.example.stellare.profile

import com.example.stellare.data.model.ReviewModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.ReviewRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeReviewDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.profile.ProfileViewModel
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun profileViewModel(
        currentUserId: String,
        users: List<UserModel>,
        userDataSource: FakeUserDataSource = FakeUserDataSource(users),
        reviewDataSource: FakeReviewDataSource = FakeReviewDataSource(userDataSource)
    ): Pair<ProfileViewModel, FakeReviewDataSource> {
        val userRepository = UserRepository(FakeAuthDataSource(currentUserId), userDataSource, FakeFavoriteDataSource())
        val reviewRepository = ReviewRepository(reviewDataSource)
        return ProfileViewModel(userRepository, reviewRepository) to reviewDataSource
    }

    @Test
    fun `addReview liczy srednia ocene i licznik psychologa`() = runTest(dispatcher) {
        val (vm, reviewDataSource) = profileViewModel(
            currentUserId = "pacjent1",
            users = listOf(
                UserModel(id = "pacjent1", firstName = "Jan", lastName = "Kowalski"),
                UserModel(id = "psych1", rating = 4.0, ratingCount = 2)
            )
        )

        vm.addReview("psych1", 5, "Bardzo pomocny psycholog")
        advanceUntilIdle()

        val (nowaOcena, nowyLicznik) = reviewDataSource.lastRatingUpdate!!
        assertEquals(4.333333333333333, nowaOcena, 0.0000001)
        assertEquals(3, nowyLicznik)
    }

    @Test
    fun `addReview nadaje recenzji identyfikator psycholog_recenzent i reviewerId zalogowanego`() = runTest(dispatcher) {
        val (vm, reviewDataSource) = profileViewModel(
            currentUserId = "pacjent1",
            users = listOf(
                UserModel(id = "pacjent1", firstName = "Jan", lastName = "Kowalski"),
                UserModel(id = "psych1", rating = 4.0, ratingCount = 2)
            )
        )

        vm.addReview("psych1", 5, "Bardzo pomocny psycholog")
        advanceUntilIdle()

        val zapisana = reviewDataSource.reviews.value.single()
        assertEquals("psych1_pacjent1", zapisana.reviewId)
        assertEquals("pacjent1", zapisana.reviewerId)
        assertEquals("Jan Kowalski", zapisana.reviewerName)
    }

    @Test
    fun `updateProfile zapisuje zmienione dane przez UserRepository`() = runTest(dispatcher) {
        val userDataSource = FakeUserDataSource(
            listOf(UserModel(id = "u1", firstName = "Stary", lastName = "Nazwisko", age = 20, bio = "Stare bio"))
        )
        val userRepository = UserRepository(FakeAuthDataSource("u1"), userDataSource, FakeFavoriteDataSource())
        val vm = ProfileViewModel(userRepository, ReviewRepository(FakeReviewDataSource(userDataSource)))

        vm.updateProfile(
            firstName = "Nowy",
            lastName = "Kowalski",
            age = 30,
            gender = "Kobieta",
            bio = "Nowe bio",
            specializations = "Rodzina",
            pricePerSession = 150.0
        )
        advanceUntilIdle()

        val zaktualizowany = userDataSource.users.value.single { it.id == "u1" }
        assertEquals("Nowy", zaktualizowany.firstName)
        assertEquals("Kowalski", zaktualizowany.lastName)
        assertEquals(30, zaktualizowany.age)
        assertEquals("Nowe bio", zaktualizowany.bio)
        assertEquals("Rodzina", zaktualizowany.specializations)
        assertEquals(150.0, zaktualizowany.pricePerSession, 0.0001)
    }

    @Test
    fun `currentUser udostepnia zalogowanego uzytkownika`() = runTest(dispatcher) {
        val (vm, _) = profileViewModel(
            currentUserId = "u1",
            users = listOf(UserModel(id = "u1", firstName = "Anna"))
        )
        backgroundScope.launch { vm.currentUser.collect {} }
        advanceUntilIdle()

        assertEquals("Anna", vm.currentUser.value?.firstName)
    }

    @Test
    fun `reviewsFor zwraca tylko recenzje danego psychologa`() = runTest(dispatcher) {
        val reviewDataSource = FakeReviewDataSource(FakeUserDataSource())
        reviewDataSource.reviews.value = listOf(
            ReviewModel(reviewId = "psych1_p1", psychologistId = "psych1", reviewerId = "p1"),
            ReviewModel(reviewId = "psych2_p1", psychologistId = "psych2", reviewerId = "p1")
        )
        val (vm, _) = profileViewModel(
            currentUserId = "p1",
            users = listOf(UserModel(id = "p1")),
            reviewDataSource = reviewDataSource
        )

        val wynik = mutableListOf<ReviewModel>()
        backgroundScope.launch { vm.reviewsFor("psych1").collect { wynik.addAll(it) } }
        advanceUntilIdle()

        assertEquals(1, wynik.size)
        assertEquals("psych1_p1", wynik.first().reviewId)
    }
}
