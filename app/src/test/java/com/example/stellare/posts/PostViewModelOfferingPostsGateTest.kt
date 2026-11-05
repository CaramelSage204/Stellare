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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostViewModelOfferingPostsGateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val oferta = PostModel(id = "o1", authorId = "p1", type = PostType.OFFERING_THERAPY, isActive = true)

    private fun zbuduj(signedInAs: String?): Pair<PostViewModel, RecordingPostDataSource> {
        val userRepository = UserRepository(FakeAuthDataSource(signedInAs), FakeUserDataSource(), FakeFavoriteDataSource(), FakeRatingDataSource(), FakePrivateProfileDataSource())
        val postDataSource = RecordingPostDataSource(listOf(oferta))
        return PostViewModel(PostRepository(postDataSource), userRepository) to postDataSource
    }

    @Test
    fun `niezalogowany nie pyta o oferty terapii`() = runTest(dispatcher) {
        val (vm, postDataSource) = zbuduj(signedInAs = null)
        backgroundScope.launch { vm.offeringPosts.collect {} }
        runCurrent()

        assertEquals(0, postDataSource.getActivePostsCallCount(PostType.OFFERING_THERAPY))
        assertEquals(emptyList<PostModel>(), vm.offeringPosts.value)
    }

    @Test
    fun `zalogowany dostaje oferty terapii`() = runTest(dispatcher) {
        val (vm, _) = zbuduj(signedInAs = "k1")
        backgroundScope.launch { vm.offeringPosts.collect {} }
        runCurrent()

        assertEquals(listOf("o1"), vm.offeringPosts.value.map { it.id })
    }
}
