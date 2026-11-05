package com.example.stellare.feed

import com.example.stellare.fakes.FakePrivateProfileDataSource
import com.example.stellare.data.model.FavoriteModel
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.RatingModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.feed.FeedFilters
import com.example.stellare.ui.feed.FeedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

class FeedFiltersTest {

    private val ogloszenie = PostModel(
        id = "post1",
        title = "Szukam wsparcia psychologa",
        body = "Potrzebuje pomocy w trudnym okresie",
        tags = listOf("Psycholog kliniczny", "Interwent kryzysowy")
    )

    @Test
    fun `pusty zbior tagow przepuszcza ogloszenie bez tagow`() {
        assertTrue(FeedFilters(tags = emptySet()).matchesPost(ogloszenie.copy(tags = emptyList())))
    }

    @Test
    fun `pusty zbior tagow przepuszcza ogloszenie z tagami`() {
        assertTrue(FeedFilters(tags = emptySet()).matchesPost(ogloszenie))
    }

    @Test
    fun `jeden wspolny tag przepuszcza`() {
        assertTrue(FeedFilters(tags = setOf("Interwent kryzysowy")).matchesPost(ogloszenie))
    }

    @Test
    fun `brak wspolnego tagu odrzuca`() {
        assertFalse(FeedFilters(tags = setOf("Seksuolog")).matchesPost(ogloszenie))
    }

    @Test
    fun `tekst wyszukiwania pasujacy do tytulu przepuszcza`() {
        assertTrue(FeedFilters(query = "wsparcia").matchesPost(ogloszenie))
    }

    @Test
    fun `tekst wyszukiwania pasujacy do tresci przepuszcza`() {
        assertTrue(FeedFilters(query = "trudnym okresie").matchesPost(ogloszenie))
    }

    @Test
    fun `tekst wyszukiwania niepasujacy do niczego odrzuca`() {
        assertFalse(FeedFilters(query = "nieistniejaca fraza").matchesPost(ogloszenie))
    }

    @Test
    fun `tagi i tekst dzialaja lacznie a nie na zasadzie alternatywy`() {
        assertFalse(
            FeedFilters(
                tags = setOf("Interwent kryzysowy"),
                query = "nieistniejaca fraza"
            ).matchesPost(ogloszenie)
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val tani = UserModel(
        id = "p1", firstName = "Ala", age = 35, role = UserRole.PSYCHOLOGIST,
        pricePerSession = 80.0, isVerified = true
    )
    private val drogi = UserModel(
        id = "p2", firstName = "Borys", age = 45, role = UserRole.PSYCHOLOGIST,
        pricePerSession = 300.0, isVerified = true
    )

    private val oceny = mapOf("p1" to RatingModel(sum = 9, count = 2), "p2" to RatingModel(sum = 49, count = 10))

    private fun viewModel(vararg users: UserModel, ratingSource: FakeRatingDataSource = FakeRatingDataSource().apply { ratings.value = oceny }): FeedViewModel {
        val repo = UserRepository(
            FakeAuthDataSource("k1"),
            FakeUserDataSource(users.toList()),
            FakeFavoriteDataSource(),
            ratingSource,
            FakePrivateProfileDataSource()
        )
        return FeedViewModel(repo)
    }

    @Test
    fun `zmiana agregatu w ratings odswieza plakietki bez ponownego pobrania uzytkownikow`() = runTest(dispatcher) {
        val ratingSource = FakeRatingDataSource().apply { ratings.value = oceny }
        val vm = viewModel(tani, drogi, ratingSource = ratingSource)
        assertEquals("4,5", vm.authorRatingBadges.first { it["p1"] != null }["p1"])

        ratingSource.ratings.value = oceny + ("p1" to RatingModel(sum = 10, count = 2))

        assertEquals("5,0", vm.authorRatingBadges.first { it["p1"] == "5,0" }["p1"])
    }

    @Test
    fun `plakietki ocen pochodza z agregatu i pomijaja autorow bez opinii`() = runTest(dispatcher) {
        val vm = viewModel(tani, drogi, ratingSource = FakeRatingDataSource().apply {
            ratings.value = oceny + ("p3" to RatingModel())
        })
        val plakietki = vm.authorRatingBadges.first { it.isNotEmpty() }
        assertEquals("4,5", plakietki["p1"])
        assertEquals("4,9", plakietki["p2"])
        assertFalse("autor bez opinii nie moze miec plakietki", "p3" in plakietki)
    }

    @Test
    fun `przelaczenie ulubionego dziala gdy uzytkownik jest zalogowany`() = runTest(dispatcher) {
        val pacjent = UserModel(id = "k1", role = UserRole.PATIENT)
        val favoriteDataSource = FakeFavoriteDataSource()
        val repo = UserRepository(
            FakeAuthDataSource("k1"),
            FakeUserDataSource(listOf(pacjent, tani)),
            favoriteDataSource,
            FakeRatingDataSource(),
            FakePrivateProfileDataSource()
        )
        val vm = FeedViewModel(repo)

        vm.toggleFavorite("p1")
        advanceUntilIdle()

        assertEquals(listOf("p1"), favoriteDataSource.favorites.value.map { it.psychologistId })
    }

    @Test
    fun `przelaczenie usuwa istniejacy ulubiony zamiast dodawac go ponownie`() = runTest(dispatcher) {
        val pacjent = UserModel(id = "k1", role = UserRole.PATIENT)
        val favoriteDataSource = FakeFavoriteDataSource()
        favoriteDataSource.favorites.value = listOf(FavoriteModel(userId = "k1", psychologistId = "p1"))
        val repo = UserRepository(
            FakeAuthDataSource("k1"),
            FakeUserDataSource(listOf(pacjent, tani)),
            favoriteDataSource,
            FakeRatingDataSource(),
            FakePrivateProfileDataSource()
        )
        val vm = FeedViewModel(repo)

        vm.toggleFavorite("p1")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), favoriteDataSource.favorites.value.map { it.psychologistId })
    }

    @Test
    fun `psycholog widzi ulubionych pacjentow niezaleznie od filtrow kanalu`() = runTest(dispatcher) {
        val psycholog = UserModel(id = "k1", role = UserRole.PSYCHOLOGIST)
        val pacjent = UserModel(id = "pac1", firstName = "Kasia", role = UserRole.PATIENT, age = 22)
        val favoriteDataSource = FakeFavoriteDataSource()
        favoriteDataSource.favorites.value = listOf(FavoriteModel(userId = "k1", psychologistId = "pac1"))
        val repo = UserRepository(
            FakeAuthDataSource("k1"),
            FakeUserDataSource(listOf(psycholog, pacjent, tani, drogi)),
            favoriteDataSource,
            FakeRatingDataSource(),
            FakePrivateProfileDataSource()
        )
        val vm = FeedViewModel(repo)

        vm.updateFilters(FeedFilters(query = "fraza ktorej pacjent nie ma", tags = setOf("Seksuolog")))

        val ulubieni = vm.favoritePsychologists.first { it.isNotEmpty() }

        assertEquals(listOf("pac1"), ulubieni.map { it.id })
        assertTrue(ulubieni.none { it.role == UserRole.PSYCHOLOGIST })
    }
}
