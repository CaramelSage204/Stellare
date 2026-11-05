package com.example.stellare.feed

import com.example.stellare.fakes.FakePrivateProfileDataSource
import com.example.stellare.data.model.FavoriteModel
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelFavoriteIdsGateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun zbuduj(signedInAs: String?): FeedViewModel {
        val favorites = FakeFavoriteDataSource().also {
            it.favorites.value = listOf(FavoriteModel(userId = "k1", psychologistId = "p1"))
        }
        val repo = UserRepository(FakeAuthDataSource(signedInAs), FakeUserDataSource(), favorites, FakeRatingDataSource(), FakePrivateProfileDataSource())
        return FeedViewModel(repo)
    }

    @Test
    fun `zalogowany bez dokumentu uzytkownika dostaje ulubionych`() = runTest(dispatcher) {
        val vm = zbuduj(signedInAs = "k1")
        backgroundScope.launch { vm.favoriteIds.collect {} }
        runCurrent()

        assertEquals(setOf("p1"), vm.favoriteIds.value)
    }

    @Test
    fun `niezalogowany nie dostaje ulubionych`() = runTest(dispatcher) {
        val vm = zbuduj(signedInAs = null)
        backgroundScope.launch { vm.favoriteIds.collect {} }
        runCurrent()

        assertEquals(emptySet<String>(), vm.favoriteIds.value)
    }
}
