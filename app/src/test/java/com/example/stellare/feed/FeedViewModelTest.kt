package com.example.stellare.feed

import com.example.stellare.data.model.FavoriteModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
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

    private val psycholog = UserModel(
        id = "p1", firstName = "Ala", lastName = "Kowalska", age = 35,
        gender = "Kobieta", role = UserRole.PSYCHOLOGIST, isVerified = true,
        specializations = "Rodzina, Praca", pricePerSession = 120.0, rating = 4.5
    )

    @Test
    fun `domyslne filtry przepuszczaja wszystkich`() {
        assertTrue(FeedFilters().matches(psycholog))
    }

    @Test
    fun `filtr ceny odcina za drogich`() {
        assertFalse(FeedFilters(priceMax = 100.0).matches(psycholog))
        assertTrue(FeedFilters(priceMax = 120.0).matches(psycholog))
    }

    @Test
    fun `filtr wieku dziala obustronnie`() {
        assertFalse(FeedFilters(ageMin = 40).matches(psycholog))
        assertFalse(FeedFilters(ageMax = 30).matches(psycholog))
        assertTrue(FeedFilters(ageMin = 30, ageMax = 40).matches(psycholog))
    }

    @Test
    fun `filtr weryfikacji przepuszcza zweryfikowanych`() {
        assertTrue(FeedFilters(verifiedOnly = true).matches(psycholog))
        assertFalse(FeedFilters(verifiedOnly = true).matches(psycholog.copy(isVerified = false)))
    }

    @Test
    fun `wyszukiwanie dziala po nazwisku i specjalizacji`() {
        assertTrue(FeedFilters(query = "kowalska").matches(psycholog))
        assertTrue(FeedFilters(query = "rodzina").matches(psycholog))
        assertFalse(FeedFilters(query = "nieistniejace").matches(psycholog))
    }

    @Test
    fun `filtr oceny odcina slabiej ocenianych`() {
        assertFalse(FeedFilters(minRating = 4.8).matches(psycholog))
        assertTrue(FeedFilters(minRating = 4.5).matches(psycholog))
    }

    @Test
    fun `granice przedzialu wieku sa domkniete z obu stron`() {
        assertTrue(FeedFilters(ageMin = 35, ageMax = 35).matches(psycholog))
        assertFalse(FeedFilters(ageMin = 36, ageMax = 40).matches(psycholog))
        assertFalse(FeedFilters(ageMin = 30, ageMax = 34).matches(psycholog))
    }

    @Test
    fun `filtr plci odcina niepasujacych`() {
        assertTrue(FeedFilters(gender = "Kobieta").matches(psycholog))
        assertFalse(FeedFilters(gender = "Mezczyzna").matches(psycholog))
    }

    @Test
    fun `filtr specjalizacji odcina niepasujacych`() {
        assertTrue(FeedFilters(specialization = "Rodzina").matches(psycholog))
        assertFalse(FeedFilters(specialization = "Uzaleznienia").matches(psycholog))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val tani = UserModel(
        id = "p1", firstName = "Ala", age = 35, role = UserRole.PSYCHOLOGIST,
        pricePerSession = 80.0, rating = 4.5, isVerified = true
    )
    private val drogi = UserModel(
        id = "p2", firstName = "Borys", age = 45, role = UserRole.PSYCHOLOGIST,
        pricePerSession = 300.0, rating = 4.9, isVerified = true
    )

    private fun viewModel(vararg users: UserModel): FeedViewModel {
        val repo = UserRepository(
            FakeAuthDataSource("k1"),
            FakeUserDataSource(users.toList()),
            FakeFavoriteDataSource()
        )
        return FeedViewModel(repo)
    }

    @Test
    fun `filtr ceny zaweza liste psychologow`() = runTest(dispatcher) {
        val vm = viewModel(tani, drogi)
        vm.updateFilters(FeedFilters(priceMax = 100.0))
        assertEquals(listOf("p1"), vm.psychologists.first { it.isNotEmpty() }.map { it.id })
    }

    @Test
    fun `reset filtrow przywraca pelna liste`() = runTest(dispatcher) {
        val vm = viewModel(tani, drogi)
        vm.updateFilters(FeedFilters(priceMax = 100.0))
        vm.resetFilters()
        assertEquals(2, vm.psychologists.first { it.size == 2 }.size)
    }

    @Test
    fun `przelaczenie ulubionego dziala gdy uzytkownik jest zalogowany`() = runTest(dispatcher) {
        val pacjent = UserModel(id = "k1", role = UserRole.PATIENT)
        val favoriteDataSource = FakeFavoriteDataSource()
        val repo = UserRepository(
            FakeAuthDataSource("k1"),
            FakeUserDataSource(listOf(pacjent, tani)),
            favoriteDataSource
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
            favoriteDataSource
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
            favoriteDataSource
        )
        val vm = FeedViewModel(repo)

        vm.updateFilters(FeedFilters(priceMax = 1.0, ageMin = 99, query = "fraza ktorej pacjent nie ma"))

        val ulubieni = vm.favoritePsychologists.first { it.isNotEmpty() }

        assertEquals(listOf("pac1"), ulubieni.map { it.id })
        assertTrue(ulubieni.none { it.role == UserRole.PSYCHOLOGIST })
    }
}
