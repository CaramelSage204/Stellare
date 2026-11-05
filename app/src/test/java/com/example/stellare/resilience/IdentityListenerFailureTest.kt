package com.example.stellare.resilience

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
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
import com.example.stellare.ui.auth.AuthStatus
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.navigation.AuthRedirect
import com.example.stellare.ui.navigation.Routes
import com.example.stellare.ui.profile.ProfileViewModel
import com.google.firebase.firestore.FirebaseFirestoreException
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Trzy objawy jednego zrodla: zaproszenie do nadpisania profilu, ucieczka z ekranu i wieczny
 * wskaznik w kalendarzu karmia sie z tego samego nasluchu dokumentu uzytkownika.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IdentityListenerFailureTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val psycholog = UserModel(
        id = "p1", firstName = "Jan", lastName = "Lis", role = UserRole.PSYCHOLOGIST, pricePerSession = 180.0
    )

    private val odmowa = FirebaseFirestoreException(
        "permission denied", FirebaseFirestoreException.Code.PERMISSION_DENIED
    )

    private fun repository(users: FakeUserDataSource, signedInAs: String?) = UserRepository(
        FakeAuthDataSource(signedInAs), users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)
    )

    private fun authViewModel(users: FakeUserDataSource, signedInAs: String?): AuthViewModel {
        val auth = FakeAuthDataSource(signedInAs)
        return AuthViewModel(
            UserRepository(auth, users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)),
            auth
        )
    }

    private fun profileViewModel(users: FakeUserDataSource, signedInAs: String?) = ProfileViewModel(
        repository(users, signedInAs),
        ReviewRepository(FakeReviewDataSource(FakeRatingDataSource())),
        PostRepository(FakePostDataSource())
    )

    @Test
    fun `pierwszy start bez sieci i z pusta pamiecia podreczna nie prowadzi na zakladanie profilu`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        users.servedFromCache.value = true
        val vm = authViewModel(users, signedInAs = "p1")
        backgroundScope.launch { vm.authStatus.collect {} }

        advanceUntilIdle()

        assertNotEquals(
            "zalogowany psycholog z pusta pamiecia podreczna i bez sieci dostawal ekran " +
                "\"Uzupelnij profil\"; wypelniony formularz NADPISUJE jego prawdziwy profil " +
                "po powrocie sieci",
            AuthStatus.PROFILE_INCOMPLETE,
            vm.authStatus.value
        )
        assertNotEquals(
            "nieudany odczyt nie moze tez wylogowac zalogowanego uzytkownika",
            AuthStatus.LOGGED_OUT,
            vm.authStatus.value
        )
        assertEquals(AuthStatus.PROFILE_UNAVAILABLE, vm.authStatus.value)
        assertNotEquals(
            "bramka nawigacji nie moze z ekranu startowego odeslac na zakladanie profilu",
            Routes.COMPLETE_PROFILE,
            AuthRedirect.target(vm.authStatus.value, Routes.SPLASH)
        )
    }

    @Test
    fun `uniewaznienie tokenu w trakcie sesji nie wyrzuca uzytkownika z ekranu`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(psycholog))
        val vm = authViewModel(users, signedInAs = "p1")
        val statusy = mutableListOf<AuthStatus>()
        backgroundScope.launch { vm.authStatus.collect { statusy.add(it) } }
        advanceUntilIdle()
        assertEquals(AuthStatus.LOGGED_IN, vm.authStatus.value)

        users.listenerFailure.value = odmowa
        advanceUntilIdle()

        assertEquals(
            "po uniewaznieniu tokenu odczyt pada; stan ma mowic \"nie wiem\", nie \"nie masz profilu\". " +
                "Przebieg: $statusy",
            AuthStatus.PROFILE_UNAVAILABLE,
            vm.authStatus.value
        )
        assertFalse(
            "awaria odczytu nie moze wyrzucic zalogowanego uzytkownika na ekran powitalny. Przebieg: $statusy",
            statusy.contains(AuthStatus.LOGGED_OUT)
        )
        assertFalse(
            "awaria odczytu nie moze zaprosic do zalozenia nowego profilu. Przebieg: $statusy",
            statusy.contains(AuthStatus.PROFILE_INCOMPLETE)
        )
        assertNull(
            "uzytkownik ma zostac na kalendarzu, na ktorym stal w chwili awarii",
            AuthRedirect.target(vm.authStatus.value, Routes.CALENDAR)
        )
    }

    @Test
    fun `uniewaznienie tokenu przestaje przedstawiac dane jako aktualne`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(psycholog))
        val vm = profileViewModel(users, signedInAs = "p1")
        backgroundScope.launch { vm.currentUser.collect {} }
        backgroundScope.launch { vm.profileReadFailure.collect {} }
        advanceUntilIdle()
        assertEquals(psycholog, vm.currentUser.value)
        assertNull("przed awaria nie ma o czym mowic", vm.profileReadFailure.value)

        users.listenerFailure.value = odmowa
        advanceUntilIdle()

        assertNotNull(
            "to jest objaw R8: po uniewaznieniu tokenu ekran rysowal pelne dane bez zadnego znaku, " +
                "ze nie udalo sie ich odswiezyc, a odswiezanie nic nie zmienialo",
            vm.profileReadFailure.value
        )
        assertEquals(
            "ostatni znany profil zostaje na ekranie — o awarii mowi komunikat, nie zgaszenie calej aplikacji",
            psycholog,
            vm.currentUser.value
        )
    }

    @Test
    fun `komunikat o awarii odczytu nie obiecuje przyszlosci`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(psycholog))
        val vm = profileViewModel(users, signedInAs = "p1")
        backgroundScope.launch { vm.profileReadFailure.collect {} }
        advanceUntilIdle()

        users.listenerFailure.value = odmowa
        advanceUntilIdle()

        val komunikat = vm.profileReadFailure.value.orEmpty()
        listOf("wróci", "wrocą", "wrócą", "za chwilę", "automatycznie", "spróbujemy").forEach { obietnica ->
            assertFalse(
                "komunikat ma mowic o stanie tu i teraz, a nie obiecywac przyszlosc, ktorej nikt nie " +
                    "gwarantuje: \"$komunikat\" zawiera \"$obietnica\"",
                komunikat.contains(obietnica, ignoreCase = true)
            )
        }
        assertFalse("komunikat nie moze byc pusty", komunikat.isBlank())
    }

    @Test
    fun `przy braku profilu i awarii odczytu ekran ma komunikat zamiast wiecznego wskaznika`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        users.servedFromCache.value = true
        val vm = profileViewModel(users, signedInAs = "p1")
        backgroundScope.launch { vm.currentUser.collect {} }
        backgroundScope.launch { vm.profileReadFailure.collect {} }

        advanceUntilIdle()

        assertNull("nie ma zadnego profilu do pokazania", vm.currentUser.value)
        assertNotNull(
            "kalendarz rysuje wskaznik postepu dokladnie wtedy, gdy profil jest nieznany; bez tego " +
                "komunikatu wskaznik kreci sie bez konca i nic go nie zatrzymuje",
            vm.profileReadFailure.value
        )
    }

    /**
     * Osobny test od poprzedniego, bo tamten przechodzi takze wtedy, gdy ponowienie nic nie robi:
     * zmiana danych sama budzi dzialajacy nasluch. Tylko licznik nowych nasluchow odrozni
     * dzialajacy przycisk od ozdoby — mutacja M8 (puste `retryProfile`) przezyla, dopoki
     * tego testu nie bylo.
     */
    @Test
    fun `ponowienie zaklada nowy nasluch dokumentu`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        users.servedFromCache.value = true
        val vm = profileViewModel(users, signedInAs = "p1")
        backgroundScope.launch { vm.currentUser.collect {} }
        backgroundScope.launch { vm.profileReadFailure.collect {} }
        advanceUntilIdle()
        assertNotNull("stan wyjsciowy to nieudany odczyt", vm.profileReadFailure.value)
        val przedPonowieniem = users.getUserSubscriptions

        vm.retryProfile()
        advanceUntilIdle()

        assertEquals(
            "przycisk ponowienia musi zalozyc NOWY nasluch dokumentu; bez tego jest ozdoba — " +
                "uzytkownik stoi przed komunikatem o nieudanym odczycie, klika i nic sie nie dzieje",
            przedPonowieniem + 1,
            users.getUserSubscriptions
        )
    }

    @Test
    fun `po przywroceniu odczytu profil jest wczytany i komunikat gasnie`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        users.servedFromCache.value = true
        val vm = profileViewModel(users, signedInAs = "p1")
        backgroundScope.launch { vm.currentUser.collect {} }
        backgroundScope.launch { vm.profileReadFailure.collect {} }
        advanceUntilIdle()
        assertNotNull(vm.profileReadFailure.value)

        users.users.value = listOf(psycholog)
        users.servedFromCache.value = false
        advanceUntilIdle()

        assertEquals(
            "po udanym odczycie ekran ma pokazac profil, a nie zostac w stanie awarii",
            psycholog,
            vm.currentUser.value
        )
        assertNull("po udanym odczycie komunikat gasnie", vm.profileReadFailure.value)
    }

    /**
     * Z1. Ponowienie, które odświeża tylko strumień ekranu, a nie bramkę nawigacji, zostawia
     * konto **bez dokumentu** uwięzione na kanale ogłoszeń: baner gaśnie (bo ekran dostał
     * odpowiedź), ale bramka trzyma stary stan, więc nie ma drogi na zakładanie profilu.
     * Oba modele widoku dostają **ten sam** [UserRepository], tak jak w `AppContainer`.
     */
    @Test
    fun `ponowienie odswieza takze bramke nawigacji, nie tylko ekran`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        val auth = FakeAuthDataSource("p1")
        val repository = UserRepository(
            auth, users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)
        )
        val authVm = AuthViewModel(repository, auth)
        val profileVm = ProfileViewModel(
            repository,
            ReviewRepository(FakeReviewDataSource(FakeRatingDataSource())),
            PostRepository(FakePostDataSource())
        )
        users.listenerFailure.value = odmowa
        backgroundScope.launch { authVm.authStatus.collect {} }
        backgroundScope.launch { profileVm.currentUser.collect {} }
        backgroundScope.launch { profileVm.profileReadFailure.collect {} }
        advanceUntilIdle()
        assertEquals(AuthStatus.PROFILE_UNAVAILABLE, authVm.authStatus.value)

        users.listenerFailure.value = null
        profileVm.retryProfile()
        advanceUntilIdle()

        assertEquals(
            "po ponowieniu bramka nawigacji musi zobaczyc nowy stan. Inaczej konto bez dokumentu " +
                "zostaje na kanale ogloszen z awatarem \"?\", bez banera i BEZ DROGI na " +
                "\"Uzupelnij profil\" — uwiezione az do restartu aplikacji",
            AuthStatus.PROFILE_INCOMPLETE,
            authVm.authStatus.value
        )
        assertEquals(
            "a bramka ma je odeslac na zakladanie profilu",
            Routes.COMPLETE_PROFILE,
            AuthRedirect.target(authVm.authStatus.value, Routes.FEED)
        )
    }

    @Test
    fun `wylogowanie dalej odsyla na ekran powitalny`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(psycholog))
        val vm = authViewModel(users, signedInAs = null)
        backgroundScope.launch { vm.authStatus.collect {} }

        advanceUntilIdle()

        assertEquals(
            "poprawka nie moze zamknac wylogowania: bez tego nikt juz nie trafi na ekran powitalny",
            AuthStatus.LOGGED_OUT,
            vm.authStatus.value
        )
        assertEquals(Routes.ONBOARDING, AuthRedirect.target(vm.authStatus.value, Routes.CALENDAR))
    }

    @Test
    fun `zalogowany z dokumentem dalej wchodzi do aplikacji`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(psycholog))
        val vm = authViewModel(users, signedInAs = "p1")
        backgroundScope.launch { vm.authStatus.collect {} }

        advanceUntilIdle()

        assertEquals(AuthStatus.LOGGED_IN, vm.authStatus.value)
        assertEquals(Routes.FEED, AuthRedirect.target(vm.authStatus.value, Routes.SPLASH))
    }
}
