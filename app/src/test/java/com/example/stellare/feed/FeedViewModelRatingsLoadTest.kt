package com.example.stellare.feed

import com.example.stellare.fakes.FakePrivateProfileDataSource
import com.example.stellare.data.model.RatingModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.feed.FeedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelRatingsLoadTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val psycholog = UserModel(id = "p1", age = 35, role = UserRole.PSYCHOLOGIST, isVerified = true)
    private val ratingSource = FakeRatingDataSource().apply {
        ratings.value = mapOf("p1" to RatingModel(sum = 9, count = 2))
    }

    private fun viewModel() = FeedViewModel(
        UserRepository(
            FakeAuthDataSource("k1"),
            FakeUserDataSource(listOf(psycholog)),
            FakeFavoriteDataSource(),
            ratingSource,
            FakePrivateProfileDataSource()
        )
    )

    @Test
    fun `awaria nasluchu ocen nie konczy strumienia plakietek i plakietki wracaja po ponowieniu`() = runTest(dispatcher) {
        ratingSource.failuresBeforeSuccess = 1
        val vm = viewModel()
        backgroundScope.launch { vm.authorRatingBadges.collect {} }
        runCurrent()

        assertEquals(emptyMap<String, String>(), vm.authorRatingBadges.value)

        advanceTimeBy(1_500)
        runCurrent()

        assertEquals(
            "po ponowieniu plakietki ocen muszą wrócić bez restartu aplikacji",
            mapOf("p1" to "4,5"),
            vm.authorRatingBadges.value
        )
    }

    @Test
    fun `plakietki otwieraja jeden nasluch kolekcji ratings`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.authorRatingBadges.collect {} }
        runCurrent()

        assertEquals(
            "dwa nasłuchy na całą kolekcję podwajają liczbę odczytów przy każdym otwarciu pulpitu",
            1,
            ratingSource.getRatingsSubscriptionCount
        )
    }
}
