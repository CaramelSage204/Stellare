package com.example.stellare.profile

import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.remote.PostDataSource
import com.example.stellare.data.remote.UserDataSource
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.ReviewRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakePostDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeReviewDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.ui.UncertainWriteMessages
import com.example.stellare.ui.profile.ProfileSaveState
import com.example.stellare.ui.profile.ProfileViewModel
import com.example.stellare.ui.profile.WRITE_TIMEOUT_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
 * Zapis do Firestore bez sieci nie konczy sie ani sukcesem, ani bledem: SDK przyjmuje go lokalnie
 * i czeka. Zmierzone na urzadzeniu: po 120 s napis "Zapisywanie..." nadal stal, a po powrocie sieci
 * zapis wyladowal na serwerze. Dlatego limit czasu nie moze konczyc sie slowem "nie udalo sie" -
 * to byloby nieprawda o tym samym zapisie, ktory za chwile zadziala.
 */
private class NeverAnsweringSaveUserDataSource(inner: UserDataSource) : UserDataSource by inner {
    override suspend fun saveUser(user: UserModel) = awaitCancellation()
}

private class NeverAnsweringPostLookup(inner: PostDataSource) : PostDataSource by inner {
    override fun getPostsByAuthor(authorId: String): Flow<List<PostModel>> = flow { awaitCancellation() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileSaveTimeoutTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val users = FakeUserDataSource(listOf(UserModel(id = "u1", firstName = "Jan", lastName = "Kowalski")))

    private fun viewModel(
        userSource: UserDataSource = users,
        postSource: PostDataSource = FakePostDataSource()
    ) = ProfileViewModel(
        UserRepository(FakeAuthDataSource("u1"), userSource, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(userSource)),
        ReviewRepository(FakeReviewDataSource(FakeRatingDataSource())),
        PostRepository(postSource)
    )

    private fun ProfileViewModel.save() = updateProfile(
        firstName = "Jan",
        lastName = "Kowalski",
        age = 30,
        gender = "Mezczyzna",
        bio = "Nowe bio",
        specializations = "",
        pricePerSession = 0.0
    )

    @Test
    fun `zapis bez odpowiedzi konczy sie komunikatem, a nie napisem Zapisywanie bez konca`() = runTest(dispatcher) {
        val vm = viewModel(userSource = NeverAnsweringSaveUserDataSource(users))

        vm.save()
        advanceTimeBy(WRITE_TIMEOUT_MS - 1)

        assertEquals(
            "przed uplywem limitu ekran slusznie pokazuje, ze zapis trwa",
            ProfileSaveState.InProgress,
            vm.saveState.value
        )

        advanceTimeBy(2)
        advanceUntilIdle()

        val state = vm.saveState.value
        assertTrue(
            "zapis, ktory nie odpowiedzial, zostawial napis \"Zapisywanie...\" na zawsze: " +
                "zmierzone 120 s bez slowa, a przycisk przez caly ten czas byl wylaczony. Stan: $state",
            state is ProfileSaveState.Uncertain
        )
        assertEquals(
            "po limicie czasu nie wiemy, czy zapis wyladowal - Firestore wysle go, gdy wroci siec. " +
                "Zdanie \"nie udalo sie\" byloby nieprawda o tym samym zapisie",
            UncertainWriteMessages.PROFILE_SAVE,
            (state as ProfileSaveState.Uncertain).message
        )
    }

    @Test
    fun `brak odpowiedzi przy synchronizacji ogloszen tez konczy czekanie`() = runTest(dispatcher) {
        val vm = viewModel(postSource = NeverAnsweringPostLookup(FakePostDataSource()))

        vm.save()
        advanceTimeBy(WRITE_TIMEOUT_MS + 1)
        advanceUntilIdle()

        val state = vm.saveState.value
        assertTrue(
            "limit czasu na samym zapisie profilu nie wystarczy: drugi krok tej samej operacji " +
                "zawiesilby ekran dokladnie tak samo. Stan: $state",
            state is ProfileSaveState.Uncertain
        )
        assertEquals(
            UncertainWriteMessages.PROFILE_POSTS_SYNC,
            (state as ProfileSaveState.Uncertain).message
        )
    }
}
