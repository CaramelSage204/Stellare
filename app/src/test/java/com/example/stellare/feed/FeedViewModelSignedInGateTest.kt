package com.example.stellare.feed

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.fakes.FakePrivateProfileDataSource
import com.example.stellare.data.model.FavoriteModel
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelSignedInGateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val psycholog = UserModel(id = "p1", age = 35, role = UserRole.PSYCHOLOGIST, isVerified = true)
    private val pacjent = UserModel(id = "k2", role = UserRole.PATIENT)

    private fun zbuduj(signedInAs: String?, favorites: List<FavoriteModel> = emptyList()): Pair<FeedViewModel, FakeUserDataSource> {
        val userDataSource = FakeUserDataSource(listOf(psycholog, pacjent))
        val favoriteDataSource = FakeFavoriteDataSource().apply { this.favorites.value = favorites }
        val repo = UserRepository(FakeAuthDataSource(signedInAs), userDataSource, favoriteDataSource, FakeRatingDataSource(), privateProfilesOf(userDataSource))
        return FeedViewModel(repo) to userDataSource
    }

    @Test
    fun `niezalogowany nie wykonuje zadnego zapytania o uzytkownikow wedlug roli`() = runTest(dispatcher) {
        val (vm, userDataSource) = zbuduj(signedInAs = null)
        backgroundScope.launch { vm.patients.collect {} }
        backgroundScope.launch { vm.favoritePsychologists.collect {} }
        runCurrent()

        assertEquals(0, userDataSource.getAllByRoleSubscriptionCount)
        assertEquals(emptyList<UserModel>(), vm.favoritePsychologists.value)
        assertEquals(emptyList<UserModel>(), vm.patients.value)
    }

    @Test
    fun `zalogowany dostaje psychologow i pacjentow z zapytan o role`() = runTest(dispatcher) {
        val (vm, userDataSource) = zbuduj(signedInAs = "k1", favorites = listOf(FavoriteModel(userId = "k1", psychologistId = "p1")))
        backgroundScope.launch { vm.favoritePsychologists.collect {} }
        backgroundScope.launch { vm.patients.collect {} }
        runCurrent()

        assertEquals(listOf("p1"), vm.favoritePsychologists.first { it.isNotEmpty() }.map { it.id })
        assertEquals(listOf("k2"), vm.patients.first { it.isNotEmpty() }.map { it.id })
        assertEquals(true, userDataSource.getAllByRoleSubscriptionCount > 0)
    }

    @Test
    fun `niezalogowany nie nasluchuje kolekcji ratings a zalogowany tak`() = runTest(dispatcher) {
        val ratingSource = FakeRatingDataSource()
        val niezalogowany = FeedViewModel(
            UserRepository(FakeAuthDataSource(null), FakeUserDataSource(listOf(psycholog)), FakeFavoriteDataSource(), ratingSource, FakePrivateProfileDataSource())
        )
        backgroundScope.launch { niezalogowany.authorRatingBadges.collect {} }
        runCurrent()
        assertEquals("reguly odmawiaja odczytu ratings niezalogowanym, wiec nasluch ma sie nie otwierac", 0, ratingSource.getRatingsSubscriptionCount)

        val zalogowany = FeedViewModel(
            UserRepository(FakeAuthDataSource("k1"), FakeUserDataSource(listOf(psycholog)), FakeFavoriteDataSource(), ratingSource, FakePrivateProfileDataSource())
        )
        backgroundScope.launch { zalogowany.authorRatingBadges.collect {} }
        runCurrent()
        assertEquals(true, ratingSource.getRatingsSubscriptionCount > 0)
    }
}
