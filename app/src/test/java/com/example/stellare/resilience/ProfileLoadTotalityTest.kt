package com.example.stellare.resilience

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.ReviewRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeReviewDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.RecordingPostDataSource
import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.ui.profile.ProfileViewModel
import com.example.stellare.ui.psychologistdetail.ProfileLoad
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `userById` wołają dwa nieosłonięte `LaunchedEffect` w `PsychologistDetailScreen` i `produceState`
 * w `VerificationRequestCard`. Korutyna kompozycji nie ma nad sobą żadnej osłony, więc wyjątek
 * stamtąd leci wprost w górę i wywraca aplikację przy wejściu na profil psychologa.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileLoadTotalityTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class Setup(val vm: ProfileViewModel, val users: FakeUserDataSource)

    private fun zbuduj(vararg documents: UserModel): Setup {
        val users = FakeUserDataSource(documents.toList())
        val ratings = FakeRatingDataSource()
        val repository = UserRepository(
            FakeAuthDataSource("k1"), users, FakeFavoriteDataSource(), ratings, privateProfilesOf(users)
        )
        val vm = ProfileViewModel(
            repository,
            ReviewRepository(FakeReviewDataSource(ratings)),
            PostRepository(RecordingPostDataSource())
        )
        return Setup(vm, users)
    }

    private fun odmowa() =
        FirebaseFirestoreException("odmowa regul", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    @Test
    fun `nieudany odczyt profilu nie wychodzi wyjatkiem z korutyny kompozycji`() = runTest(dispatcher) {
        val s = zbuduj(UserModel(id = "p1", firstName = "Jan"))
        s.users.readFailure = odmowa()

        val wynik = runCatching { s.vm.userById("p1") }

        assertTrue(
            "userById wolaja nieoslonione LaunchedEffect w PsychologistDetailScreen; wyjatek stamtad " +
                "wywraca aplikacje przy samym wejsciu na profil psychologa. Wyjatek: ${wynik.exceptionOrNull()}",
            wynik.isSuccess
        )
        assertTrue(
            "nieudany odczyt musi byc osobnym stanem; zwrocenie pustki udawaloby, ze takiego " +
                "psychologa nie ma, czyli ten sam defekt co R2 w portfelu. Stan: ${wynik.getOrNull()}",
            wynik.getOrNull() is ProfileLoad.Failed
        )
        assertEquals(
            "ekran ma powiedziec, ze profilu nie udalo sie wczytac",
            "Nie udało się wczytać profilu. Spróbuj ponownie.",
            (wynik.getOrThrow() as ProfileLoad.Failed).message
        )
    }

    @Test
    fun `brak dokumentu profilu to osobny stan od awarii odczytu`() = runTest(dispatcher) {
        val s = zbuduj()

        assertEquals(
            "nieistniejacy profil to Absent, nie Failed: nie ma tu czego ponawiac",
            ProfileLoad.Absent,
            s.vm.userById("nikogo-takiego")
        )
    }

    @Test
    fun `istniejacy profil wraca jako Loaded`() = runTest(dispatcher) {
        val s = zbuduj(UserModel(id = "p1", firstName = "Jan", lastName = "Lis"))

        val load = s.vm.userById("p1")

        assertTrue("istniejacy profil to Loaded, stan: $load", load is ProfileLoad.Loaded)
        assertEquals("Jan", (load as ProfileLoad.Loaded).user.firstName)
    }
}
