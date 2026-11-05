package com.example.stellare.account

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.AccountDeleter
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.ui.auth.AuthStatus
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.settings.AccountDeletionState
import com.example.stellare.ui.settings.AccountDeletionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDeletionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class Rig(deferredSignOutScope: CoroutineScope? = null) {
        val log = EventLog()
        val auth = AccountAuthDataSource(log, deferredSignOutScope = deferredSignOutScope)
        val users = AccountUserDataSource(log, listOf(UserModel(id = "u1")))
        val posts = AccountPostDataSource(log, listOf(post("p1")))
        val privateProfiles = AccountPrivateProfileDataSource(log)
        val repository = UserRepository(auth, users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfiles)
        val deleter = AccountDeleter(
            auth, users, privateProfiles, posts, AccountChatDataSource(), AccountNoteDataSource(log),
            AccountAppointmentDataSource(log), AccountVerificationDataSource(log)
        ) { AccountAuthDataSource.NOW }
        val viewModel = AccountDeletionViewModel(deleter, repository, auth)
        val authViewModel = AuthViewModel(repository, auth)
    }

    private fun kotlinx.coroutines.test.TestScope.observe(rig: Rig): MutableList<AuthStatus> {
        val seen = mutableListOf<AuthStatus>()
        backgroundScope.launch { rig.authViewModel.authStatus.collect { seen += it } }
        advanceUntilIdle()
        return seen
    }

    @Test
    fun `sukces konczy na wylogowaniu bez przystanku na uzupelnianiu profilu`() = runTest(dispatcher) {
        val rig = Rig()
        val seen = observe(rig)

        rig.viewModel.deleteAccount()
        advanceUntilIdle()
        runCurrent()

        assertEquals(AuthStatus.LOGGED_OUT, rig.authViewModel.authStatus.value)
        assertTrue(
            "po usunieciu konta aplikacja nie moze przez chwile uznac, ze profil jest do uzupelnienia: $seen",
            AuthStatus.PROFILE_INCOMPLETE !in seen
        )
    }

    @Test
    fun `wylogowanie, ktore dociera po zakonczeniu usuwania, tez nie robi przystanku na uzupelnianiu profilu`() =
        runTest(dispatcher) {
            val rig = Rig(deferredSignOutScope = backgroundScope)
            val seen = observe(rig)

            rig.viewModel.deleteAccount()
            advanceUntilIdle()
            runCurrent()

            assertEquals(AuthStatus.LOGGED_OUT, rig.authViewModel.authStatus.value)
            assertTrue(
                "profil zniknal, a konto jeszcze chwile istnieje; aplikacja nie moze wtedy pokazac uzupelniania profilu: $seen",
                AuthStatus.PROFILE_INCOMPLETE !in seen
            )
        }

    @Test
    fun `porazka po usunieciu profilu nie przenosi na ekran uzupelniania, dopoki uzytkownik jej nie zamknie`() =
        runTest(dispatcher) {
            val rig = Rig()
            rig.posts.failingIds += "p1"
            val seen = observe(rig)

            rig.viewModel.deleteAccount()
            advanceUntilIdle()
            runCurrent()

            assertTrue(
                "stan ma opisywac porazke, a jest ${rig.viewModel.state.value}",
                rig.viewModel.state.value is AccountDeletionState.Failed
            )
            assertEquals(
                "ekran z komunikatem o porazce musi zostac na miejscu, bo inaczej uzytkownik go nie przeczyta",
                AuthStatus.LOGGED_IN,
                rig.authViewModel.authStatus.value
            )

            rig.viewModel.dismissOutcome()
            advanceUntilIdle()
            runCurrent()

            assertEquals(
                "po zamknieciu komunikatu stan odpowiada rzeczywistosci: konto jest, profilu nie ma",
                AuthStatus.PROFILE_INCOMPLETE,
                rig.authViewModel.authStatus.value
            )
            assertTrue(seen.isNotEmpty())
        }

    @Test
    fun `porazka przed usunieciem profilu nie blokuje obserwacji profilu`() = runTest(dispatcher) {
        val rig = Rig()
        rig.users.deleteFailure = IllegalStateException("odmowa")
        observe(rig)

        rig.viewModel.deleteAccount()
        advanceUntilIdle()
        runCurrent()

        val state = rig.viewModel.state.value
        assertTrue("stan ma opisywac porazke, a jest $state", state is AccountDeletionState.Failed)
        assertEquals(false, (state as AccountDeletionState.Failed).profileGone)
        rig.users.users.value = emptyList()
        advanceUntilIdle()
        runCurrent()
        assertEquals(
            "obserwacja profilu ma dzialac, wiec znikniecie dokumentu jest widoczne",
            AuthStatus.PROFILE_INCOMPLETE,
            rig.authViewModel.authStatus.value
        )
    }

    @Test
    fun `nieswieze logowanie daje stan odsylajacy do logowania i niczego nie kasuje`() = runTest(dispatcher) {
        val rig = Rig()
        rig.auth.lastSignIn = AccountAuthDataSource.NOW - 3_600_000
        observe(rig)

        rig.viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(AccountDeletionState.RecentLoginRequired, rig.viewModel.state.value)
        assertTrue("nic nie wolno bylo skasowac: ${rig.log.events}", rig.log.events.isEmpty())
        assertEquals(AuthStatus.LOGGED_IN, rig.authViewModel.authStatus.value)
    }

    @Test
    fun `sciezka ponownego logowania wylogowuje`() = runTest(dispatcher) {
        val rig = Rig()
        observe(rig)

        rig.viewModel.signOutToRelogin()
        advanceUntilIdle()

        assertEquals(1, rig.auth.signOutCount)
        assertEquals(AuthStatus.LOGGED_OUT, rig.authViewModel.authStatus.value)
    }

    @Test
    fun `drugie wywolanie w trakcie usuwania nie uruchamia usuwania ponownie`() = runTest(dispatcher) {
        val rig = Rig()
        observe(rig)

        rig.viewModel.deleteAccount()
        rig.viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(
            "konto w Auth ma byc usuniete jeden raz",
            1,
            rig.log.events.count { it == "auth:delete" }
        )
    }
}
