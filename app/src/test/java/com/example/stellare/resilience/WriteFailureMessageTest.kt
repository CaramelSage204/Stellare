package com.example.stellare.resilience

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.VerificationRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeVerificationDataSource
import com.example.stellare.fakes.RecordingPostDataSource
import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.ui.posts.PostViewModel
import com.example.stellare.ui.verification.VerificationViewModel
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Zwrócony `false` bez komunikatu nie dociera do użytkownika: zgłoszenie zostaje na liście,
 * ogłoszenie nie pojawia się na ekranie, a jedyną pozostałą informacją jest log.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WriteFailureMessageTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val psycholog = UserModel(id = "p1", firstName = "Jan", lastName = "Lis", pricePerSession = 80.0)

    private fun odmowa() =
        FirebaseFirestoreException("odmowa regul", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    private fun repository(users: FakeUserDataSource) = UserRepository(
        FakeAuthDataSource("p1"), users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)
    )

    @Test
    fun `nieudane zatwierdzenie zgloszenia mowi administratorowi, ze decyzja nie zostala zapisana`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(psycholog))
        val requests = FakeVerificationDataSource(users).apply { decideFailure = odmowa() }
        val vm = VerificationViewModel(VerificationRepository(requests), repository(users), { 0L })

        val rozstrzygnieto = vm.decide("p1", approved = true, note = "")

        assertFalse("odmowa zapisu nie jest rozstrzygnieciem", rozstrzygnieto)
        assertNotNull(
            "nieudane ZATWIERDZENIE musi powiedziec o sobie: bez komunikatu zgloszenie zostaje na " +
                "liscie i zaprasza do kolejnych klikniec, a administrator nie wie, ze nic sie nie zapisalo",
            vm.submitError.value
        )
        assertEquals(
            "Nie udało się zapisać decyzji. Zgłoszenie zostało bez zmian. Spróbuj ponownie.",
            vm.submitError.value
        )
    }

    @Test
    fun `nieudana publikacja ogloszenia mowi o tym w kreatorze`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(psycholog))
        val posts = RecordingPostDataSource().apply { saveFailure = odmowa() }
        val vm = PostViewModel(PostRepository(posts), repository(users))

        val opublikowano = vm.publish("Tytul", "Tresc", emptyList())

        assertFalse("odmowa zapisu nie jest publikacja", opublikowano)
        assertNotNull(
            "kreator ogloszenia ma powiedziec, ze publikacja sie nie udala; sam zwrocony false " +
                "zostawia ekran bez slowa i wyglada jak brak reakcji na przycisk",
            vm.publishError.value
        )
    }
}
