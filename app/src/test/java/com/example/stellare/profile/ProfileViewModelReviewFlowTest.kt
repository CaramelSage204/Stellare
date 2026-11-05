package com.example.stellare.profile

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.data.model.RatingModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.ReviewRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakePostDataSource
import com.example.stellare.fakes.FakeReviewDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.profile.ProfileViewModel
import com.example.stellare.ui.profile.ReviewSubmitState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelReviewFlowTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val users = FakeUserDataSource(
        listOf(
            UserModel(id = "pacjent1", firstName = "Jan", lastName = "Kowalski"),
            UserModel(id = "psych1")
        )
    )
    private val ratingSource = FakeRatingDataSource().apply {
        ratings.value = mapOf("psych1" to RatingModel(sum = 8, count = 2))
    }
    private val reviewSource = FakeReviewDataSource(ratingSource)

    private fun viewModel() = ProfileViewModel(
        UserRepository(FakeAuthDataSource("pacjent1"), users, FakeFavoriteDataSource(), ratingSource, privateProfilesOf(users)),
        ReviewRepository(reviewSource),
        PostRepository(FakePostDataSource())
    )

    @Test
    fun `bez znacznika odbytej wizyty pacjent nie moze wystawic opinii`() = runTest(dispatcher) {
        assertFalse(viewModel().hasCompletedSessionWith("psych1"))
    }

    @Test
    fun `ze znacznikiem odbytej wizyty pacjent moze wystawic opinie`() = runTest(dispatcher) {
        reviewSource.completions += "psych1_pacjent1"
        assertTrue(viewModel().hasCompletedSessionWith("psych1"))
    }

    @Test
    fun `znacznik innego pacjenta nie daje prawa do opinii`() = runTest(dispatcher) {
        reviewSource.completions += "psych1_ktos_inny"
        assertFalse(viewModel().hasCompletedSessionWith("psych1"))
    }

    @Test
    fun `kontencja przy wystawianiu opinii mowi o ocenianiu`() = runTest(dispatcher) {
        val aborting = object : com.example.stellare.data.remote.ReviewDataSource by reviewSource {
            override suspend fun saveReviewWithRating(review: com.example.stellare.data.model.ReviewModel, psychologistId: String) {
                throw com.google.firebase.firestore.FirebaseFirestoreException(
                    "ABORTED: too much contention",
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED
                )
            }
        }
        val vm = ProfileViewModel(
            UserRepository(FakeAuthDataSource("pacjent1"), users, FakeFavoriteDataSource(), ratingSource, privateProfilesOf(users)),
            ReviewRepository(aborting),
            PostRepository(FakePostDataSource())
        )
        vm.addReview("psych1", 5, "Super")
        advanceUntilIdle()

        assertEquals(ReviewSubmitState.Error(com.example.stellare.ui.UserFacingError.RATING_TRY_AGAIN), vm.reviewState.value)
    }

    @Test
    fun `udana opinia konczy sie stanem Success i zmienia srednia`() = runTest(dispatcher) {
        reviewSource.completions += "psych1_pacjent1"
        val vm = viewModel()
        vm.addReview("psych1", 5, "Super")
        advanceUntilIdle()

        assertEquals(ReviewSubmitState.Success, vm.reviewState.value)
        val psych = ratingSource.ratings.value.getValue("psych1")
        assertEquals(13, psych.sum)
        assertEquals(3, psych.count)
    }

    @Test
    fun `druga opinia tej samej pary daje komunikat zamiast awarii i nie zmienia sredniej`() = runTest(dispatcher) {
        reviewSource.completions += "psych1_pacjent1"
        val vm = viewModel()
        vm.addReview("psych1", 5, "Pierwsza")
        advanceUntilIdle()
        vm.addReview("psych1", 1, "Druga")
        advanceUntilIdle()

        val stan = vm.reviewState.value
        assertTrue(stan is ReviewSubmitState.Error)
        assertFalse((stan as ReviewSubmitState.Error).message.contains("PERMISSION_DENIED"))
        val psych = ratingSource.ratings.value.getValue("psych1")
        assertEquals(13, psych.sum)
        assertEquals(3, psych.count)
    }
}
