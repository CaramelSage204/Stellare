package com.example.stellare.wallet

import com.example.stellare.data.model.PrivateProfile
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.ReviewRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.WalletRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakePostDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeReviewDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.ui.profile.ProfileViewModel
import com.example.stellare.ui.wallet.WalletViewModel
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletBalanceSourceTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val users = FakeUserDataSource(listOf(UserModel(id = "k1")), balances = mapOf("k1" to 130))

    private fun repository(uid: String? = "k1") =
        UserRepository(FakeAuthDataSource(uid), users, FakeFavoriteDataSource(), FakeRatingDataSource(), users.privateProfiles)

    private fun walletViewModel(uid: String? = "k1") =
        WalletViewModel(WalletRepository(FakeWalletDataSource(users)), repository(uid))

    private fun profileViewModel(uid: String? = "k1") = ProfileViewModel(
        repository(uid),
        ReviewRepository(FakeReviewDataSource(FakeRatingDataSource())),
        PostRepository(FakePostDataSource())
    )

    @Test
    fun `saldo portfela pochodzi z dokumentu prywatnego`() = runTest(dispatcher) {
        val vm = walletViewModel()
        backgroundScope.launch { vm.balance.collect {} }
        runCurrent()

        assertEquals("kontrola przyrządu: saldo nie może być zerem z braku odczytu", 130, vm.balance.value)
    }

    @Test
    fun `saldo portfela podaza za zmiana dokumentu prywatnego`() = runTest(dispatcher) {
        val vm = walletViewModel()
        backgroundScope.launch { vm.balance.collect {} }
        runCurrent()

        users.privateProfiles.setBalance("k1", 210)
        runCurrent()

        assertEquals(210, vm.balance.value)
    }

    @Test
    fun `nieudany odczyt salda jest ponawiany, a nie buforowany jako zero na zawsze`() = runTest(dispatcher) {
        users.privateProfiles.readFailuresBeforeSuccess = 1
        val vm = walletViewModel()
        backgroundScope.launch { vm.balance.collect {} }
        runCurrent()
        assertEquals("po porażce odczytu saldo pokazuje zero do czasu ponowienia", 0, vm.balance.value)

        advanceTimeBy(1_500)
        runCurrent()

        assertEquals("raz zbuforowana porażka zostaje na ekranie na zawsze", 130, vm.balance.value)
        assertEquals("odczyt miał być ponowiony dokładnie raz", 2, users.privateProfiles.subscriptionCount)
    }

    @Test
    fun `kolejne nieudane odczyty sa ponawiane z rosnacym opoznieniem`() = runTest(dispatcher) {
        users.privateProfiles.readFailuresBeforeSuccess = 3
        val vm = walletViewModel()
        backgroundScope.launch { vm.balance.collect {} }
        runCurrent()

        advanceTimeBy(1_500)
        runCurrent()
        assertEquals("po 1,5 s ponowiono dopiero raz, więc saldo nadal nieznane", 0, vm.balance.value)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(130, vm.balance.value)
        assertEquals(4, users.privateProfiles.subscriptionCount)
    }

    @Test
    fun `trwala odmowa odczytu salda nie jest ponawiana, a ekran nie pada`() = runTest(dispatcher) {
        users.privateProfiles.readFailure =
            FirebaseFirestoreException("PERMISSION_DENIED", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        val vm = walletViewModel()
        backgroundScope.launch { vm.balance.collect {} }
        runCurrent()
        advanceTimeBy(5 * 60_000)
        runCurrent()

        assertEquals(0, vm.balance.value)
        assertEquals("trwała odmowa jest ponawiana w nieskończoność", 1, users.privateProfiles.subscriptionCount)
    }

    @Test
    fun `przejsciowy blad odczytu jest nadal ponawiany`() = runTest(dispatcher) {
        users.privateProfiles.readFailure =
            FirebaseFirestoreException("UNAVAILABLE", FirebaseFirestoreException.Code.UNAVAILABLE)
        val vm = walletViewModel()
        backgroundScope.launch { vm.balance.collect {} }
        runCurrent()
        advanceTimeBy(70_000)
        runCurrent()

        assertTrue("błąd przejściowy ma być ponawiany: ${users.privateProfiles.subscriptionCount}", users.privateProfiles.subscriptionCount >= 3)
    }

    @Test
    fun `niezalogowany nie ma salda i nie pyta o dokument prywatny`() = runTest(dispatcher) {
        val vm = walletViewModel(uid = null)
        backgroundScope.launch { vm.balance.collect {} }
        runCurrent()

        assertEquals(0, vm.balance.value)
        assertEquals(0, users.privateProfiles.subscriptionCount)
    }

    @Test
    fun `doladowanie zwieksza saldo odczytywane z dokumentu prywatnego`() = runTest(dispatcher) {
        val vm = walletViewModel()
        backgroundScope.launch { vm.balance.collect {} }
        runCurrent()

        vm.topUp(70)
        advanceUntilIdle()

        assertEquals(200, vm.balance.value)
        assertEquals(200, users.balanceOf("k1"))
    }

    @Test
    fun `profil udostepnia telefon, e-mail i saldo z dokumentu prywatnego`() = runTest(dispatcher) {
        users.privateProfiles.profiles.value =
            mapOf("k1" to PrivateProfile(phone = "+48500600700", email = "k1@example.com", coinsBalance = 130))
        val vm = profileViewModel()
        backgroundScope.launch { vm.privateProfile.collect {} }
        runCurrent()

        assertEquals(PrivateProfile("+48500600700", "k1@example.com", 130), vm.privateProfile.value)
    }

    @Test
    fun `nieudany odczyt danych prywatnych w profilu jest ponawiany`() = runTest(dispatcher) {
        users.privateProfiles.readFailuresBeforeSuccess = 1
        val vm = profileViewModel()
        backgroundScope.launch { vm.privateProfile.collect {} }
        runCurrent()
        assertNull(vm.privateProfile.value)

        advanceTimeBy(1_500)
        runCurrent()

        assertEquals("raz zbuforowana porażka zostawiłaby profil bez salda i numeru na zawsze", 130, vm.privateProfile.value?.coinsBalance)
    }

    @Test
    fun `profil niezalogowanego nie ma danych prywatnych`() = runTest(dispatcher) {
        val vm = profileViewModel(uid = null)
        backgroundScope.launch { vm.privateProfile.collect {} }
        runCurrent()

        assertNull(vm.privateProfile.value)
    }
}
