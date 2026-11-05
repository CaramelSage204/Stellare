package com.example.stellare.profile

import com.example.stellare.fakes.FakePrivateProfileDataSource
import com.example.stellare.data.model.RatingModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.ReviewRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakePostDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeReviewDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelRatingTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val ratings = FakeRatingDataSource().apply {
        ratings.value = mapOf("psych1" to RatingModel(sum = 45, count = 10))
    }

    private fun viewModel(signedInAs: String?) = ProfileViewModel(
        UserRepository(
            FakeAuthDataSource(signedInAs),
            FakeUserDataSource(listOf(UserModel(id = "psych1"))),
            FakeFavoriteDataSource(),
            ratings,
            FakePrivateProfileDataSource()
        ),
        ReviewRepository(FakeReviewDataSource(ratings)),
        PostRepository(FakePostDataSource())
    )

    @Test
    fun `ekran Moje oceny pokazuje agregat z kolekcji ratings`() = runTest(dispatcher) {
        assertEquals(RatingModel(sum = 45, count = 10), viewModel("psych1").ratingFor("psych1").first())
    }

    @Test
    fun `psycholog bez dokumentu ratings ma ocene zero zamiast wyjatku`() = runTest(dispatcher) {
        assertEquals(RatingModel(), viewModel("psych2").ratingFor("psych2").first())
    }

    @Test
    fun `niezalogowany nie dostaje agregatu`() = runTest(dispatcher) {
        assertEquals(RatingModel(), viewModel(null).ratingFor("psych1").first())
    }
}
