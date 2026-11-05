package com.example.stellare.profile

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.remote.PostDataSource
import com.example.stellare.data.remote.UserDataSource
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.ReviewRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakePostDataSource
import com.example.stellare.fakes.FakeReviewDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.profile.ProfileSaveState
import com.example.stellare.ui.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

private class FailingSaveUserDataSource(private val inner: UserDataSource) : UserDataSource by inner {
    override suspend fun saveUser(user: UserModel) {
        throw IllegalStateException("PERMISSION_DENIED: Missing or insufficient permissions.")
    }
}

private class FailingPostLookup(private val inner: PostDataSource) : PostDataSource by inner {
    override fun getPostsByAuthor(authorId: String): Flow<List<PostModel>> {
        throw IllegalStateException("UNAVAILABLE")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelSaveStateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val users = FakeUserDataSource(
        listOf(UserModel(id = "u1", firstName = "Jan", lastName = "Kowalski", bio = "Stare bio"))
    )

    private fun viewModel(
        userSource: UserDataSource = users,
        postSource: PostDataSource = FakePostDataSource(),
        signedInAs: String = "u1"
    ) = ProfileViewModel(
        UserRepository(FakeAuthDataSource(signedInAs), userSource, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(userSource)),
        ReviewRepository(FakeReviewDataSource(FakeRatingDataSource())),
        PostRepository(postSource)
    )

    private fun ProfileViewModel.save(bio: String = "Nowe bio") = updateProfile(
        firstName = "Jan",
        lastName = "Kowalski",
        age = 30,
        gender = "Mezczyzna",
        bio = bio,
        specializations = "",
        pricePerSession = 0.0
    )

    @Test
    fun `przed zapisem stan to Idle`() = runTest(dispatcher) {
        assertEquals(ProfileSaveState.Idle, viewModel().saveState.value)
    }

    @Test
    fun `w trakcie zapisu stan to InProgress`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.save()
        assertEquals(ProfileSaveState.InProgress, vm.saveState.value)
        advanceUntilIdle()
    }

    @Test
    fun `udany zapis konczy sie Success i zmienia dane`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.save()
        advanceUntilIdle()
        assertEquals(ProfileSaveState.Success, vm.saveState.value)
        assertEquals("Nowe bio", users.users.value.single().bio)
    }

    @Test
    fun `nieudany zapis konczy sie Error z polskim komunikatem bez tekstu Firebase`() = runTest(dispatcher) {
        val vm = viewModel(userSource = FailingSaveUserDataSource(users))
        vm.save()
        advanceUntilIdle()
        val stan = vm.saveState.value
        assertTrue(stan is ProfileSaveState.Error)
        val komunikat = (stan as ProfileSaveState.Error).message
        assertEquals("Nie udało się zapisać profilu. Spróbuj ponownie.", komunikat)
        assertFalse(komunikat.contains("PERMISSION_DENIED"))
        assertEquals("Stare bio", users.users.value.single().bio)
    }

    @Test
    fun `blad synchronizacji ogloszen nie jest raportowany jako sukces`() = runTest(dispatcher) {
        val vm = viewModel(postSource = FailingPostLookup(FakePostDataSource()))
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.saveState.value is ProfileSaveState.Error)
    }

    @Test
    fun `brak zalogowanego uzytkownika konczy sie Error`() = runTest(dispatcher) {
        val vm = viewModel(signedInAs = "nikt")
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.saveState.value is ProfileSaveState.Error)
    }

    @Test
    fun `resetSaveState po bledzie wraca do Idle`() = runTest(dispatcher) {
        val vm = viewModel(userSource = FailingSaveUserDataSource(users))
        vm.save()
        advanceUntilIdle()
        vm.resetSaveState()
        assertEquals(ProfileSaveState.Idle, vm.saveState.value)
    }
}
