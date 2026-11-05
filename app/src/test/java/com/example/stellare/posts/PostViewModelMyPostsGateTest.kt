package com.example.stellare.posts

import com.example.stellare.fakes.FakePrivateProfileDataSource
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.RecordingPostDataSource
import com.example.stellare.ui.posts.PostViewModel
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
class PostViewModelMyPostsGateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val mojePosty = PostModel(id = "m1", authorId = "k1", type = PostType.SEEKING_PSYCHOLOGIST, isActive = true)

    private fun zbuduj(signedInAs: String?): PostViewModel {
        val userRepository = UserRepository(FakeAuthDataSource(signedInAs), FakeUserDataSource(), FakeFavoriteDataSource(), FakeRatingDataSource(), FakePrivateProfileDataSource())
        return PostViewModel(PostRepository(RecordingPostDataSource(listOf(mojePosty))), userRepository)
    }

    @Test
    fun `zalogowany bez dokumentu uzytkownika dostaje swoje ogloszenia`() = runTest(dispatcher) {
        val vm = zbuduj(signedInAs = "k1")
        backgroundScope.launch { vm.myPosts.collect {} }
        runCurrent()

        assertEquals(listOf("m1"), vm.myPosts.value.map { it.id })
    }

    @Test
    fun `niezalogowany nie dostaje ogloszen`() = runTest(dispatcher) {
        val vm = zbuduj(signedInAs = null)
        backgroundScope.launch { vm.myPosts.collect {} }
        runCurrent()

        assertEquals(emptyList<PostModel>(), vm.myPosts.value)
    }
}
