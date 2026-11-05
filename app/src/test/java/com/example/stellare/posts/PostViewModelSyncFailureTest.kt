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
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.RecordingPostDataSource
import com.example.stellare.fakes.privateProfilesOf
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostViewModelSyncFailureTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val autor = UserModel(
        id = "psych1", role = UserRole.PSYCHOLOGIST, isVerified = true, pricePerSession = 100.0
    )

    private val nieaktualne = PostModel(
        id = "p1", authorId = "psych1", type = PostType.OFFERING_THERAPY,
        authorSnapshot = AuthorSnapshot(isVerified = false), priceCoins = 100
    )

    @Test
    fun `odmowa zapisu przy uzgadnianiu ogloszen nie wychodzi z korutyny i nie zabija uzgadniania`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(autor))
        val userRepository = UserRepository(
            FakeAuthDataSource(autor.id), users, FakeFavoriteDataSource(), FakeRatingDataSource(),
            privateProfilesOf(users)
        )
        val postDataSource = RecordingPostDataSource(listOf(nieaktualne))
        postDataSource.saveFailure = IllegalStateException(
            "PERMISSION_DENIED: Write failed at posts/p1"
        )

        PostViewModel(PostRepository(postDataSource), userRepository)
        advanceUntilIdle()

        assertEquals(
            "uzgadnianie nie podjęło nawet pierwszej próby zapisu — test nie bada tego, co miał badać",
            1,
            postDataSource.saveAttemptCount
        )

        users.users.value = listOf(autor.copy(pricePerSession = 120.0))
        advanceUntilIdle()

        assertEquals(
            "odrzucony zapis ogłoszenia wyszedł z korutyny w init: korutyna uzgadniania padła" +
                " po pierwszej odmowie i nie ponowiła niczego, a w produkcji ten wyjątek nie ma" +
                " kto przechwycić — FATAL EXCEPTION przy każdym starcie i konto nie do odratowania",
            2,
            postDataSource.saveAttemptCount
        )
        assertEquals(
            "żaden z odrzuconych zapisów nie miał się udać — atrapa przestała odwzorowywać odmowę reguł",
            0,
            postDataSource.saveCount
        )
    }

    @Test
    fun `po ustaniu odmowy uzgadnianie zapisuje ogloszenie, wiec osloniecie nie polyka takze udanych zapisow`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(autor))
        val userRepository = UserRepository(
            FakeAuthDataSource(autor.id), users, FakeFavoriteDataSource(), FakeRatingDataSource(),
            privateProfilesOf(users)
        )
        val postDataSource = RecordingPostDataSource(listOf(nieaktualne))
        postDataSource.saveFailure = IllegalStateException(
            "PERMISSION_DENIED: Write failed at posts/p1"
        )

        PostViewModel(PostRepository(postDataSource), userRepository)
        advanceUntilIdle()

        postDataSource.saveFailure = null
        users.users.value = listOf(autor.copy(pricePerSession = 120.0))
        advanceUntilIdle()

        assertEquals(
            "po ustaniu odmowy uzgodnienie nie zapisało ceny z profilu: osłona zamieniła awarię na ciszę",
            120,
            postDataSource.posts.value.single { it.id == "p1" }.priceCoins
        )
    }
}
