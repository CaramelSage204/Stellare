package com.example.stellare.posts

import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.remote.PostDataSource
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakePostDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.ui.posts.DEACTIVATE_FAILED_MESSAGE
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class OdmawiajaceUkrycie(delegate: PostDataSource) : PostDataSource by delegate {
    override suspend fun deactivatePost(postId: String) {
        throw IllegalStateException("PERMISSION_DENIED: Missing or insufficient permissions.")
    }
}

/**
 * Ukrycie ogloszenia konczylo sie samym wpisem do logu. Dzis w `app/src/main` nie ma zadnej
 * kontrolki wolajacej [PostViewModel.deactivate] - zadne miejsce w interfejsie nie wywoluje tej
 * operacji - wiec nie da sie tego zobaczyc na zrzucie. Powierzchnia bledu jest tu po to, zeby
 * kontrolka, ktora kiedys powstanie, nie powstala od razu jako kolejny niemy przycisk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PostDeactivateErrorTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(postSource: PostDataSource): Pair<PostViewModel, FakePostDataSource> {
        val base = postSource as? FakePostDataSource ?: FakePostDataSource()
        val autor = UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST, pricePerSession = 100.0)
        val users = FakeUserDataSource(listOf(autor))
        val userRepository = UserRepository(
            FakeAuthDataSource(autor.id), users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)
        )
        return PostViewModel(PostRepository(postSource), userRepository) to base
    }

    @Test
    fun `nieudane ukrycie ogloszenia mowi, ze ogloszenie nadal jest widoczne`() = runTest(dispatcher) {
        val zrodlo = FakePostDataSource()
        zrodlo.posts.value = listOf(PostModel(id = "p1", authorId = "psych1", isActive = true))
        val (vm, _) = viewModel(OdmawiajaceUkrycie(zrodlo))

        vm.deactivate("p1")
        advanceUntilIdle()

        assertEquals(
            "ogloszenie zostalo aktywne, wiec milczenie po kliknieciu jest obietnica bez pokrycia",
            DEACTIVATE_FAILED_MESSAGE,
            vm.deactivateError.value
        )
        assertTrue("scenariusz traci sens, jesli ogloszenie jednak zostalo ukryte", zrodlo.posts.value.single().isActive)
    }

    @Test
    fun `udane ukrycie nie zostawia komunikatu`() = runTest(dispatcher) {
        val zrodlo = FakePostDataSource()
        zrodlo.posts.value = listOf(PostModel(id = "p1", authorId = "psych1", isActive = true))
        val (vm, _) = viewModel(zrodlo)

        vm.deactivate("p1")
        advanceUntilIdle()

        assertNull(vm.deactivateError.value)
    }
}
