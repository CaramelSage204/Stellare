package com.example.stellare.resilience

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.remote.UserDataSource
import com.example.stellare.data.repository.ChatRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeChatDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeMessageDataSource
import com.example.stellare.fakes.FakeNoteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.ui.chat.ChatViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `catch (Exception)` bez przerzutu łapie też anulowanie i zamienia je w zwykłą wartość zwrotną.
 * Wywołujący traktuje ją wtedy jak odpowiedź serwera, choć zakres już nie żyje — dlatego każdy
 * `catch (Exception)` w korutynie ma przed sobą `catch (CancellationException) { throw e }`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CancellationPropagationTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class GatedUserDataSource(private val inner: FakeUserDataSource) : UserDataSource by inner {
        val gate = CompletableDeferred<Unit>()

        override suspend fun getUserOnce(uid: String): UserModel? {
            gate.await()
            return inner.getUserOnce(uid)
        }
    }

    @Test
    fun `anulowanie w trakcie otwierania czatu wychodzi anulowaniem, nie wartoscia zwrotna`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(UserModel(id = "p1")))
        val gated = GatedUserDataSource(users)
        val vm = ChatViewModel(
            ChatRepository(FakeChatDataSource(), FakeMessageDataSource(), FakeNoteDataSource()),
            UserRepository(
                FakeAuthDataSource("p1"), gated, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(gated)
            ),
            { 0L }
        )

        var wynik: Result<String?>? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            wynik = runCatching { vm.openChatWith("k1") }
        }
        runCurrent()
        job.cancel()
        runCurrent()

        assertTrue(
            "anulowany zakres nie moze dostac zwykłej wartosci zwrotnej: polkniete anulowanie wyglada " +
                "jak odpowiedz serwera „nie udalo sie otworzyc czatu”, a praca zyje po anulowaniu zakresu. " +
                "Zwrot: $wynik",
            wynik?.exceptionOrNull() is CancellationException
        )
    }
}
