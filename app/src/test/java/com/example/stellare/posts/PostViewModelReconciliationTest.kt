package com.example.stellare.posts

import com.example.stellare.data.model.AuthorSnapshot
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.RecordingPostDataSource
import com.example.stellare.ui.posts.PostViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostViewModelReconciliationTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun zbuduj(autor: UserModel, posty: List<PostModel>): Pair<PostViewModel, RecordingPostDataSource> {
        val userDataSource = FakeUserDataSource(listOf(autor))
        val userRepository = UserRepository(FakeAuthDataSource(autor.id), userDataSource, FakeFavoriteDataSource())
        val postDataSource = RecordingPostDataSource(posty)
        val vm = PostViewModel(PostRepository(postDataSource), userRepository)
        return vm to postDataSource
    }

    @Test
    fun `po zalogowaniu uzgadnia isVerified zmieniony przez administratora i zapisuje takze aktualna cene`() = runTest(dispatcher) {
        val autor = UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST, isVerified = true, pricePerSession = 100.0)
        val nieaktualny = PostModel(
            id = "p1", authorId = autor.id, type = PostType.OFFERING_THERAPY,
            authorSnapshot = AuthorSnapshot(isVerified = false), priceCoins = 100
        )
        val (_, postDataSource) = zbuduj(autor, listOf(nieaktualny))

        advanceUntilIdle()

        assertEquals(1, postDataSource.saveCount)
        val zaktualizowany = postDataSource.posts.value.single { it.id == "p1" }
        assertTrue(zaktualizowany.authorSnapshot.isVerified)
        assertEquals(100, zaktualizowany.priceCoins)
    }

    @Test
    fun `nie przepisuje wlasnych ogloszen przy kazdym wejsciu do aplikacji gdy juz sa zgodne z profilem`() = runTest(dispatcher) {
        val autor = UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST, isVerified = true, pricePerSession = 100.0)
        val zgodny = PostModel(
            id = "p1", authorId = autor.id, type = PostType.OFFERING_THERAPY,
            authorSnapshot = AuthorSnapshot(isVerified = true), priceCoins = 100
        )
        val (_, postDataSource) = zbuduj(autor, listOf(zgodny))

        advanceUntilIdle()

        assertEquals(0, postDataSource.saveCount)
    }

    @Test
    fun `bez zalogowanego uzytkownika uzgodnienie nie wykonuje zadnego zapytania o ogloszenia`() = runTest(dispatcher) {
        val userDataSource = FakeUserDataSource(emptyList())
        val userRepository = UserRepository(FakeAuthDataSource(null), userDataSource, FakeFavoriteDataSource())
        val postDataSource = RecordingPostDataSource()

        PostViewModel(PostRepository(postDataSource), userRepository)
        advanceUntilIdle()

        assertEquals(0, postDataSource.getPostsByAuthorCallCount)
        assertEquals(0, postDataSource.saveCount)
    }
}
