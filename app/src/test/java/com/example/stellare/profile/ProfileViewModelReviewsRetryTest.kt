package com.example.stellare.profile

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.data.model.ReviewModel
import com.example.stellare.data.remote.ReviewDataSource
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
import com.example.stellare.ui.psychologistdetail.ReviewsLoad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class ReviewDataSourceRzucajacyDoOdwolania(
    private val delegate: FakeReviewDataSource
) : ReviewDataSource by delegate {
    var rzuca = true

    override fun getReviewsForPsychologist(psychologistId: String): Flow<List<ReviewModel>> =
        if (rzuca) flow { throw IllegalStateException("PERMISSION_DENIED: Missing or insufficient permissions.") }
        else delegate.getReviewsForPsychologist(psychologistId)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelReviewsRetryTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `ponowienie po bledzie wymusza nowa subskrypcje i daje wczytane opinie`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        val fake = FakeReviewDataSource(FakeRatingDataSource())
        fake.reviews.value = listOf(ReviewModel(reviewId = "r1", psychologistId = "psych1", rating = 5))
        val zrodlo = ReviewDataSourceRzucajacyDoOdwolania(fake)
        val viewModel = ProfileViewModel(
            UserRepository(FakeAuthDataSource("k1"), users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)),
            ReviewRepository(zrodlo),
            PostRepository(FakePostDataSource())
        )
        val emisje = mutableListOf<ReviewsLoad>()
        backgroundScope.launch { viewModel.reviewsLoadFor("psych1").toList(emisje) }
        runCurrent()
        assertTrue(emisje.last() is ReviewsLoad.Failed)

        zrodlo.rzuca = false
        viewModel.retryReviews()
        runCurrent()

        val ostatnia = emisje.last()
        assertTrue("Po ponowieniu oczekiwano Loaded, a jest $ostatnia", ostatnia is ReviewsLoad.Loaded)
        assertEquals(listOf("r1"), (ostatnia as ReviewsLoad.Loaded).reviews.map { it.reviewId })
    }
}
