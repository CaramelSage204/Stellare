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
import com.example.stellare.ui.psychologistdetail.ReviewSummary
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class ReviewDataSourceZBledemNasluchu(
    private val delegate: FakeReviewDataSource
) : ReviewDataSource by delegate {
    override fun getReviewsForPsychologist(psychologistId: String): Flow<List<ReviewModel>> = flow {
        throw IllegalStateException("PERMISSION_DENIED: Missing or insufficient permissions.")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelReviewsLoadTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(reviewSource: ReviewDataSource): ProfileViewModel {
        val users = FakeUserDataSource()
        return ProfileViewModel(
            UserRepository(FakeAuthDataSource("k1"), users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)),
            ReviewRepository(reviewSource),
            PostRepository(FakePostDataSource())
        )
    }

    private fun zrodloZBledem() = ReviewDataSourceZBledemNasluchu(FakeReviewDataSource(FakeRatingDataSource()))

    @Test
    fun `blad strumienia opinii daje stan niepowodzenia a nie cisze`() = runTest(dispatcher) {
        val emisje = mutableListOf<ReviewsLoad>()
        backgroundScope.launch { viewModel(zrodloZBledem()).reviewsLoadFor("psych1").toList(emisje) }
        runCurrent()

        assertEquals(1, emisje.size)
        val niepowodzenie = emisje.single() as ReviewsLoad.Failed
        assertFalse(niepowodzenie.message.contains("PERMISSION_DENIED"))
        assertTrue(niepowodzenie.message.isNotBlank())
    }

    @Test
    fun `stan niepowodzenia rozni sie od wczytywania i od pustki`() {
        val wczytywanie = ReviewSummary.fromLoad(null)
        val pustka = ReviewSummary.fromLoad(ReviewsLoad.Loaded(emptyList()))
        val niepowodzenie = ReviewSummary.fromLoad(ReviewsLoad.Failed("Nie udało się wczytać opinii."))

        assertTrue(niepowodzenie.isFailed)
        assertFalse(niepowodzenie.isLoading)
        assertFalse(niepowodzenie.isEmpty)
        assertFalse(niepowodzenie.hasReviews)
        assertFalse(niepowodzenie.showHistogram)
        assertEquals("Nie udało się wczytać opinii.", niepowodzenie.failureMessage)
        assertFalse(wczytywanie.isFailed)
        assertFalse(pustka.isFailed)
        assertTrue(wczytywanie.isLoading)
        assertTrue(pustka.isEmpty)
    }

    @Test
    fun `reviewsFor nie przekazuje bledu strumienia dalej`() = runTest(dispatcher) {
        val emisje = mutableListOf<List<ReviewModel>>()
        backgroundScope.launch { viewModel(zrodloZBledem()).reviewsFor("psych1").toList(emisje) }
        runCurrent()

        assertEquals(emptyList<List<ReviewModel>>(), emisje)
    }
}
