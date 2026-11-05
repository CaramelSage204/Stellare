package com.example.stellare.resilience

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.data.repository.ChatRepository
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.VerificationRepository
import com.example.stellare.fakes.FakeAppointmentDataSource
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeChatDataSource
import com.example.stellare.fakes.FakeEscrowDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeMessageDataSource
import com.example.stellare.fakes.FakeNoteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeVerificationDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.fakes.RecordingPostDataSource
import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.ui.calendar.BookingState
import com.example.stellare.ui.calendar.CalendarViewModel
import com.example.stellare.ui.calendar.TransferState
import com.example.stellare.ui.chat.ChatViewModel
import com.example.stellare.ui.posts.PostViewModel
import com.example.stellare.ui.verification.DocumentScanCompressionResult
import com.example.stellare.ui.verification.VerificationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.TimeZone

/**
 * Po naprawie R2 odczyt profilu rzuca, zamiast zwracać `null`. Każda ścieżka zapisu musi ten
 * wyjątek zatrzymać u siebie: w korutynie kompozycji (`PostComposerScreen`, `VerificationScreen`,
 * `AdminPanelScreen`) nie ma nad nim żadnej osłony, więc nieprzechwycony wyszedłby wprost w górę.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileReadFailureTest {

    private val dispatcher = StandardTestDispatcher()
    private val teraz = 1_790_000_000_000L
    private val awaria = IOException("odczyt profilu padl")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val pacjent = UserModel(id = "k1", firstName = "Ewa", lastName = "Kot", role = UserRole.PATIENT)
    private val psycholog = UserModel(
        id = "p1", firstName = "Jan", lastName = "Lis", role = UserRole.PSYCHOLOGIST, pricePerSession = 80.0
    )

    private fun users(vararg documents: UserModel) = FakeUserDataSource(
        documents.toList(),
        balances = documents.associate { it.id to 500 }
    )

    private fun repositoryOf(users: FakeUserDataSource, signedInAs: String) = UserRepository(
        FakeAuthDataSource(signedInAs), users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)
    )

    private fun wolnyTermin(id: String) = AppointmentModel(
        appointmentId = id, psychologistId = "p1", patientId = null,
        startsAtMillis = teraz + 3_600_000L, status = "FREE", priceCoins = 80
    )

    private fun zarezerwowanyTermin(id: String) = AppointmentModel(
        appointmentId = id, psychologistId = "p1", patientId = "k1",
        startsAtMillis = teraz + 3_600_000L, status = "BOOKED", priceCoins = 80
    )

    private fun calendar(users: FakeUserDataSource, signedInAs: String, appointments: List<AppointmentModel>): CalendarViewModel {
        val source = FakeAppointmentDataSource(appointments, users, FakeWalletDataSource(users))
        return CalendarViewModel(
            AppointmentRepository(source, FakeEscrowDataSource(source)),
            repositoryOf(users, signedInAs),
            { TimeZone.getTimeZone("Europe/Warsaw") },
            { teraz }
        )
    }

    @Test
    fun `rezerwacja po nieudanym odczycie profilu konczy sie komunikatem, nie wyjatkiem z korutyny`() = runTest(dispatcher) {
        val users = users(pacjent, psycholog)
        val vm = calendar(users, "k1", listOf(wolnyTermin("t1")))
        users.readFailure = awaria

        vm.book(wolnyTermin("t1"))
        advanceUntilIdle()

        val state = vm.bookingState.value
        assertTrue(
            "rezerwacja musi skonczyc sie komunikatem dla uzytkownika; nieudany odczyt profilu " +
                "nie moze zostawic ekranu w stanie trwajacej rezerwacji ani wyjsc wyjatkiem z korutyny, " +
                "bo to sciezka, po ktorej przenosza sie monety. Stan: $state",
            state is BookingState.Error
        )
        assertEquals(
            "komunikat ma mowic o nieudanym odczycie profilu",
            "Nie udało się odczytać Twojego profilu. Spróbuj ponownie.",
            (state as BookingState.Error).message
        )
    }

    @Test
    fun `odwolanie wizyty po nieudanym odczycie profilu konczy sie komunikatem, nie wyjatkiem z korutyny`() = runTest(dispatcher) {
        val users = users(pacjent, psycholog)
        val vm = calendar(users, "k1", listOf(zarezerwowanyTermin("t1")))
        users.readFailure = awaria

        vm.cancel("t1")
        advanceUntilIdle()

        val state = vm.transferState.value
        assertTrue(
            "odwolanie wizyty musi skonczyc sie komunikatem; inaczej wyjatek wychodzi z korutyny " +
                "na sciezce zwrotu monet, a ekran zostaje w stanie trwajacej operacji. Stan: $state",
            state is TransferState.Failed
        )
        assertEquals(
            "komunikat ma mowic o nieudanym odczycie profilu",
            "Nie udało się odczytać Twojego profilu. Spróbuj ponownie.",
            (state as TransferState.Failed).message
        )
    }

    @Test
    fun `wystawienie terminu po nieudanym odczycie profilu konczy sie komunikatem`() = runTest(dispatcher) {
        val users = users(psycholog)
        val vm = calendar(users, "p1", emptyList())
        users.readFailure = awaria

        vm.addFreeSlot(teraz + 7_200_000L)
        advanceUntilIdle()

        val state = vm.transferState.value
        assertTrue(
            "wystawienie terminu po nieudanym odczycie profilu musi powiedziec o tym uzytkownikowi, " +
                "a nie zniknac bez sladu. Stan: $state",
            state is TransferState.Failed
        )
    }

    @Test
    fun `publikacja ogloszenia po nieudanym odczycie profilu zwraca falsz z komunikatem, nie rzuca`() = runTest(dispatcher) {
        val users = users(psycholog)
        val vm = PostViewModel(PostRepository(RecordingPostDataSource()), repositoryOf(users, "p1"))
        users.readFailure = awaria

        val opublikowano = runCatching { vm.publish("Tytul", "Tresc", emptyList()) }

        assertTrue(
            "publish nie moze rzucic: wolaja go z korutyny kompozycji w PostComposerScreen, " +
                "gdzie nad wyjatkiem nie ma zadnej oslony. Wyjatek: ${opublikowano.exceptionOrNull()}",
            opublikowano.isSuccess
        )
        assertFalse("nieudany odczyt profilu nie moze liczyc sie jako publikacja", opublikowano.getOrThrow())
        assertEquals(
            "kreator ogloszenia ma pokazac, ze profilu nie udalo sie odczytac",
            "Nie udało się odczytać Twojego profilu. Spróbuj ponownie.",
            vm.publishError.value
        )
    }

    @Test
    fun `zgloszenie weryfikacyjne po nieudanym odczycie profilu zwraca falsz z komunikatem, nie rzuca`() = runTest(dispatcher) {
        val users = users(psycholog)
        val vm = VerificationViewModel(
            VerificationRepository(FakeVerificationDataSource(users)), repositoryOf(users, "p1"), { teraz }
        )
        users.readFailure = awaria

        val wyslano = runCatching {
            vm.submit(DocumentScanCompressionResult.Success("c2thbg=="), "ID_CARD")
        }

        assertTrue(
            "submit nie moze rzucic: wolaja go z korutyny kompozycji w VerificationScreen. " +
                "Wyjatek: ${wyslano.exceptionOrNull()}",
            wyslano.isSuccess
        )
        assertFalse("nieudany odczyt profilu nie moze liczyc sie jako wyslane zgloszenie", wyslano.getOrThrow())
        assertNotNull("ekran weryfikacji ma pokazac powod", vm.submitError.value)
    }

    @Test
    fun `decyzja weryfikacyjna po nieudanym odczycie profilu zwraca falsz z komunikatem, nie rzuca`() = runTest(dispatcher) {
        val users = users(psycholog)
        val vm = VerificationViewModel(
            VerificationRepository(FakeVerificationDataSource(users)), repositoryOf(users, "p1"), { teraz }
        )
        users.readFailure = awaria

        val rozstrzygnieto = runCatching { vm.decide("p1", approved = true, note = "") }

        assertTrue(
            "decide nie moze rzucic: wolaja go z dwoch korutyn kompozycji w AdminPanelScreen. " +
                "Wyjatek: ${rozstrzygnieto.exceptionOrNull()}",
            rozstrzygnieto.isSuccess
        )
        assertFalse("nieudany odczyt profilu nie moze liczyc sie jako decyzja", rozstrzygnieto.getOrThrow())
        assertNotNull("panel weryfikacji ma pokazac powod", vm.submitError.value)
    }

    @Test
    fun `zapis notatki po nieudanym odczycie profilu nie zapisuje nic i nie wywraca korutyny`() = runTest(dispatcher) {
        val users = users(psycholog)
        val notes = FakeNoteDataSource()
        val vm = ChatViewModel(
            ChatRepository(FakeChatDataSource(), FakeMessageDataSource(), notes), repositoryOf(users, "p1"), { teraz }
        )
        users.readFailure = awaria

        vm.saveNote("c1", "Tytul", "Tresc")
        advanceUntilIdle()

        assertEquals(
            "nieudany odczyt profilu nie moze zapisac notatki bez autora ani wyjsc wyjatkiem z korutyny",
            0,
            notes.notes.value.size
        )
    }
}
