package com.example.stellare.posts

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
import kotlinx.coroutines.launch
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

/**
 * Reguła Firestore na /posts pozwala na nieprzefiltrowane zapytanie o
 * SEEKING_PSYCHOLOGIST wyłącznie psychologowi, studentowi psychologii albo
 * autorowi ogłoszenia (patrz firebase/firestore.rules). Dla pacjenta takie
 * zapytanie kończy się PERMISSION_DENIED, więc seekingPosts nie może go
 * nawet próbować wykonać - stąd RecordingPostDataSource.getActivePostsCallCount
 * jako dowód, że zapytanie w ogóle nie poleciało, a nie tylko wróciło puste.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PostViewModelSeekingPostsRoleTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val zgloszeniePacjenta = PostModel(
        id = "s1", authorId = "pat1", type = PostType.SEEKING_PSYCHOLOGIST, isActive = true
    )

    private fun zbuduj(user: UserModel?, posty: List<PostModel> = emptyList()): Pair<PostViewModel, RecordingPostDataSource> {
        val userDataSource = FakeUserDataSource(if (user != null) listOf(user) else emptyList())
        val userRepository = UserRepository(FakeAuthDataSource(user?.id), userDataSource, FakeFavoriteDataSource())
        val postDataSource = RecordingPostDataSource(posty)
        val vm = PostViewModel(PostRepository(postDataSource), userRepository)
        return vm to postDataSource
    }

    @Test
    fun `psycholog widzi zgloszenia pacjentow w seekingPosts`() = runTest(dispatcher) {
        val psycholog = UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST)
        val (vm, _) = zbuduj(psycholog, listOf(zgloszeniePacjenta))
        backgroundScope.launch { vm.seekingPosts.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("s1"), vm.seekingPosts.value.map { it.id })
    }

    @Test
    fun `student psychologii widzi zgloszenia pacjentow w seekingPosts`() = runTest(dispatcher) {
        val student = UserModel(id = "student1", role = UserRole.PSYCHOLOGY_STUDENT)
        val (vm, _) = zbuduj(student, listOf(zgloszeniePacjenta))
        backgroundScope.launch { vm.seekingPosts.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("s1"), vm.seekingPosts.value.map { it.id })
    }

    @Test
    fun `pacjent dostaje pusta liste w seekingPosts mimo dostepnych zgloszen w zrodle`() = runTest(dispatcher) {
        val pacjent = UserModel(id = "pat1", role = UserRole.PATIENT)
        val (vm, _) = zbuduj(pacjent, listOf(zgloszeniePacjenta))
        backgroundScope.launch { vm.seekingPosts.collect {} }
        advanceUntilIdle()

        assertTrue(vm.seekingPosts.value.isEmpty())
    }

    @Test
    fun `pacjent subskrybujac seekingPosts nie wywoluje ani jednego zapytania o aktywne ogloszenia poszukujace`() = runTest(dispatcher) {
        val pacjent = UserModel(id = "pat1", role = UserRole.PATIENT)
        val (vm, postDataSource) = zbuduj(pacjent, listOf(zgloszeniePacjenta))
        backgroundScope.launch { vm.seekingPosts.collect {} }
        advanceUntilIdle()

        assertEquals(0, postDataSource.getActivePostsCallCount(PostType.SEEKING_PSYCHOLOGIST))
    }

    @Test
    fun `przed zalogowaniem seekingPosts nie wywoluje zadnego zapytania o ogloszenia poszukujace`() = runTest(dispatcher) {
        val (vm, postDataSource) = zbuduj(null, listOf(zgloszeniePacjenta))
        backgroundScope.launch { vm.seekingPosts.collect {} }
        advanceUntilIdle()

        assertEquals(0, postDataSource.getActivePostsCallCount(PostType.SEEKING_PSYCHOLOGIST))
        assertTrue(vm.seekingPosts.value.isEmpty())
    }
}
