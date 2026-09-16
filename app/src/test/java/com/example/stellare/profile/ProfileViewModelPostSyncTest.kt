package com.example.stellare.profile

import com.example.stellare.data.model.AuthorSnapshot
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.ReviewRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeReviewDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.RecordingPostDataSource
import com.example.stellare.ui.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelPostSyncTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val psycholog = UserModel(
        id = "psych1", role = UserRole.PSYCHOLOGIST,
        firstName = "Stary", lastName = "Kowalski", age = 40, gender = "Mezczyzna",
        bio = "Stare bio", isVerified = false, pricePerSession = 100.0
    )

    private fun wlasnePost(autor: UserModel) = PostModel(
        id = "p1", authorId = autor.id, type = PostType.OFFERING_THERAPY,
        title = "Konsultacje", body = "Opis",
        authorSnapshot = AuthorSnapshot(
            firstName = autor.firstName, lastName = autor.lastName,
            profileImageUri = autor.profileImageUri, age = autor.age,
            gender = autor.gender, isVerified = autor.isVerified
        ),
        priceCoins = autor.pricePerSession.toInt()
    )

    private fun zbuduj(autor: UserModel, posty: List<PostModel>): Pair<ProfileViewModel, RecordingPostDataSource> {
        val userDataSource = FakeUserDataSource(listOf(autor))
        val userRepository = UserRepository(FakeAuthDataSource(autor.id), userDataSource, FakeFavoriteDataSource())
        val reviewRepository = ReviewRepository(FakeReviewDataSource(userDataSource))
        val postDataSource = RecordingPostDataSource(posty)
        val postRepository = PostRepository(postDataSource)
        return ProfileViewModel(userRepository, reviewRepository, postRepository) to postDataSource
    }

    @Test
    fun `zmiana imienia w profilu aktualizuje authorSnapshot we wlasnym ogloszeniu`() = runTest(dispatcher) {
        val wlasny = wlasnePost(psycholog)
        val (vm, postDataSource) = zbuduj(psycholog, listOf(wlasny))

        vm.updateProfile(
            firstName = "Nowy",
            lastName = psycholog.lastName,
            age = psycholog.age,
            gender = psycholog.gender,
            bio = psycholog.bio,
            specializations = psycholog.specializations,
            pricePerSession = psycholog.pricePerSession
        )
        advanceUntilIdle()

        val zaktualizowany = postDataSource.posts.value.single { it.id == "p1" }
        assertEquals("Nowy", zaktualizowany.authorSnapshot.firstName)
    }

    @Test
    fun `updateProfile nie przepisuje ogloszenia gdy zmienilo sie tylko pole spoza snapshotu i ceny`() = runTest(dispatcher) {
        val wlasny = wlasnePost(psycholog)
        val (vm, postDataSource) = zbuduj(psycholog, listOf(wlasny))

        vm.updateProfile(
            firstName = psycholog.firstName,
            lastName = psycholog.lastName,
            age = psycholog.age,
            gender = psycholog.gender,
            bio = "Zupelnie nowe bio",
            specializations = psycholog.specializations,
            pricePerSession = psycholog.pricePerSession
        )
        advanceUntilIdle()

        assertEquals(0, postDataSource.saveCount)
    }
}
