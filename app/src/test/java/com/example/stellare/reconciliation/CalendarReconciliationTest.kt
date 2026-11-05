package com.example.stellare.reconciliation

import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.CompletionOutcome
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.EscrowTestWorld
import com.example.stellare.fakes.EscrowTestWorld.Companion.DAY
import com.example.stellare.fakes.EscrowTestWorld.Companion.HOUR
import com.example.stellare.fakes.EscrowTestWorld.Companion.START
import com.example.stellare.fakes.EscrowTestWorld.Companion.slot
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeEscrowDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.ui.UncertainWriteMessages
import com.example.stellare.ui.UserFacingError
import com.example.stellare.ui.calendar.BookingState
import com.example.stellare.ui.calendar.CalendarViewModel
import com.example.stellare.ui.calendar.TransferState
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarReconciliationTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun unavailable() = FirebaseFirestoreException("brak sieci", Code.UNAVAILABLE)
    private fun denied() = FirebaseFirestoreException("odmowa regul", Code.PERMISSION_DENIED)

    private fun world(vararg slots: com.example.stellare.data.model.AppointmentModel, patient: Int = 200) = EscrowTestWorld(
        patients = mapOf("k1" to patient, "k2" to 200),
        psychologists = mapOf("p1" to 50, "p2" to 50),
        slots = slots.toList()
    )

    private fun viewModel(w: EscrowTestWorld, uid: String) = CalendarViewModel(
        AppointmentRepository(w.source, FakeEscrowDataSource(w.source)),
        UserRepository(FakeAuthDataSource(uid), w.users, FakeFavoriteDataSource(), FakeRatingDataSource(), w.users.privateProfiles),
        { TimeZone.getTimeZone("Europe/Warsaw") },
        { w.now }
    )

    private fun ledgerCount(w: EscrowTestWorld, id: String) = w.wallet.transactions.value.count { it.transactionId == id }

    private fun TestScope.book(w: EscrowTestWorld, vm: CalendarViewModel, id: String = "t1") {
        vm.book(w.appointment(id))
        advanceUntilIdle()
    }

    private suspend fun EscrowTestWorld.bookDirectly(id: String = "t1", patient: String = "k1") {
        val slot = appointment(id)
        source.bookAppointmentAtomically(id, patient, slot.psychologistId, slot.priceCoins, "")
    }

    @Test
    fun `rezerwacja po utracie odpowiedzi - sukces, depozyt, jeden wpis ksiegi, suma monet bez zmian`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        val before = w.coinsInSystem()
        val vm = viewModel(w, "k1")
        w.source.transactions.loseResponseAfterCommit = unavailable()

        book(w, vm)

        assertEquals("zapis musiał dojść do bazy, inaczej test nie bada utraty odpowiedzi", 1, w.source.transactions.commits)
        assertEquals(BookingState.Success, vm.bookingState.value)
        assertEquals(EscrowModel.HELD, w.escrow("t1")?.status)
        assertEquals(1, ledgerCount(w, "t1_k1"))
        assertEquals(120, w.balance("k1"))
        assertEquals(before, w.coinsInSystem())
    }

    @Test
    fun `rezerwacja, ktora nie doszla - porazka z komunikatem o braku sieci i nic nie zmienione`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        val vm = viewModel(w, "k1")
        w.source.transactions.failBeforeCommit = unavailable()

        book(w, vm)

        assertEquals(BookingState.Error(UserFacingError.NO_CONNECTION), vm.bookingState.value)
        assertEquals("FREE", w.appointment("t1").status)
        assertNull(w.escrow("t1"))
        assertEquals(200, w.balance("k1"))
        assertEquals(0, w.wallet.transactions.value.size)
        assertEquals("zapis i sprawdzenie, dwie transakcje", 2, w.source.transactions.attempts)
    }

    @Test
    fun `kontencja przy rezerwacji, ktora nie doszla - ogolny komunikat, bez slowa o ocenianiu`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        val vm = viewModel(w, "k1")
        w.source.transactions.failBeforeCommit = FirebaseFirestoreException("ABORTED: contention", Code.ABORTED)

        book(w, vm)

        val message = (vm.bookingState.value as BookingState.Error).message
        assertEquals(UserFacingError.TRY_AGAIN, message)
        assertFalse(
            "interfejs opisuje operację (ocenianie), której użytkownik nie wykonywał: $message",
            message.contains("ocen", ignoreCase = true)
        )
        assertEquals("FREE", w.appointment("t1").status)
    }

    @Test
    fun `rezerwacja doszla, a sprawdzenie tez zawiodlo - komunikat o niewiedzy, nie o porazce`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        val vm = viewModel(w, "k1")
        w.source.transactions.loseResponseAfterCommit = unavailable()
        w.source.transactions.readFailureAfterLostResponse = unavailable()

        book(w, vm)

        assertEquals("zapis musiał dojść do bazy", 1, w.source.transactions.commits)
        assertEquals(BookingState.Error(UncertainWriteMessages.BOOKING), vm.bookingState.value)
        assertEquals("BOOKED", w.appointment("t1").status)
    }

    @Test
    fun `rezerwacja, ktora nie doszla, a sprawdzenie zawiodlo - tez niewiedza, bo klient nie ma dowodu`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        val vm = viewModel(w, "k1")
        w.source.transactions.failBeforeCommit = unavailable()
        w.source.transactions.readOnlyFailure = unavailable()

        book(w, vm)

        assertEquals(BookingState.Error(UncertainWriteMessages.BOOKING), vm.bookingState.value)
        assertEquals("FREE", w.appointment("t1").status)
    }

    @Test
    fun `odmowa regul przy rezerwacji nie uruchamia sprawdzenia`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        val vm = viewModel(w, "k1")
        w.source.transactions.failBeforeCommit = denied()

        book(w, vm)

        assertEquals(BookingState.Error("Nie udało się zarezerwować terminu. Spróbuj ponownie."), vm.bookingState.value)
        assertEquals("sama próba zapisu, bez odczytu sprawdzającego", 1, w.source.transactions.attempts)
    }

    @Test
    fun `za malo monet - komunikat domenowy bez zmian i bez sprawdzenia`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80), patient = 10)
        val vm = viewModel(w, "k1")

        book(w, vm)

        assertEquals(BookingState.Error("Za mało monet w portfelu."), vm.bookingState.value)
        assertEquals(1, w.source.transactions.attempts)
        assertEquals(10, w.balance("k1"))
    }

    @Test
    fun `ponowna rezerwacja po utracie odpowiedzi nie tworzy drugiego wpisu ani nie pobiera monet drugi raz`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        val before = w.coinsInSystem()
        val vm = viewModel(w, "k1")
        w.source.transactions.loseResponseAfterCommit = unavailable()
        w.source.transactions.readFailureAfterLostResponse = unavailable()
        book(w, vm)
        assertEquals(BookingState.Error(UncertainWriteMessages.BOOKING), vm.bookingState.value)
        w.source.transactions.readOnlyFailure = null
        w.source.transactions.readFailureAfterLostResponse = null
        vm.resetBookingState()

        book(w, vm)

        assertEquals(BookingState.Success, vm.bookingState.value)
        assertEquals("druga próba nie może zatwierdzić niczego nowego", 1, w.source.transactions.commits)
        assertEquals(1, ledgerCount(w, "t1_k1"))
        assertEquals(1, w.source.escrows.value.size)
        assertEquals(120, w.balance("k1"))
        assertEquals(before, w.coinsInSystem())
    }

    @Test
    fun `rezerwacja ponowiona po prawdziwej porazce zaklada dokladnie jeden depozyt`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        val vm = viewModel(w, "k1")
        w.source.transactions.failBeforeCommit = unavailable()
        book(w, vm)
        vm.resetBookingState()

        book(w, vm)

        assertEquals(BookingState.Success, vm.bookingState.value)
        assertEquals(1, w.source.transactions.commits)
        assertEquals(1, ledgerCount(w, "t1_k1"))
        assertEquals(120, w.balance("k1"))
    }

    @Test
    fun `odwolanie ze zwrotem doszlo, odpowiedz nie wrocila - pacjent widzi zwrot, a monety wrocily raz`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        val before = w.coinsInSystem()
        val vm = viewModel(w, "k1")
        val commitsBefore = w.source.transactions.commits
        w.source.transactions.loseResponseAfterCommit = unavailable()

        vm.cancel("t1")
        advanceUntilIdle()

        assertEquals("odwołanie musiało dojść do bazy", commitsBefore + 1, w.source.transactions.commits)
        assertEquals(TransferState.Cancelled(CancelOutcome.Refunded(80)), vm.transferState.value)
        assertEquals(CancelOutcome.Refunded(80), vm.cancelOutcome.value)
        assertEquals(EscrowModel.REFUNDED, w.escrow("t1")?.status)
        assertEquals(1, ledgerCount(w, "t1_refund"))
        assertEquals(200, w.balance("k1"))
        assertEquals(before, w.coinsInSystem())
    }

    @Test
    fun `odwolanie w oknie 24 h doszlo, odpowiedz nie wrocila - wynik mowi o wyplacie dla psychologa`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80, startsAt = START + 2 * HOUR))
        w.bookDirectly()
        val vm = viewModel(w, "k1")
        w.source.transactions.loseResponseAfterCommit = unavailable()

        vm.cancel("t1")
        advanceUntilIdle()

        assertEquals(TransferState.Cancelled(CancelOutcome.Released(80)), vm.transferState.value)
        assertEquals(130, w.balance("p1"))
        assertEquals(1, ledgerCount(w, "t1_p1"))
    }

    @Test
    fun `odwolanie, ktore nie doszlo - porazka, wizyta nadal zarezerwowana, depozyt nietkniety`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        val vm = viewModel(w, "k1")
        w.source.transactions.failBeforeCommit = unavailable()

        vm.cancel("t1")
        advanceUntilIdle()

        assertEquals(TransferState.Failed(UserFacingError.NO_CONNECTION), vm.transferState.value)
        assertEquals("BOOKED", w.appointment("t1").status)
        assertEquals(EscrowModel.HELD, w.escrow("t1")?.status)
        assertNull(vm.cancelOutcome.value)
    }

    @Test
    fun `odwolanie doszlo, a sprawdzenie zawiodlo - niewiedza`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        val vm = viewModel(w, "k1")
        w.source.transactions.loseResponseAfterCommit = unavailable()
        w.source.transactions.readFailureAfterLostResponse = unavailable()

        vm.cancel("t1")
        advanceUntilIdle()

        assertEquals(TransferState.Failed(UncertainWriteMessages.CANCELLATION), vm.transferState.value)
        assertEquals("CANCELLED", w.appointment("t1").status)
    }

    @Test
    fun `odmowa regul przy odwolaniu nie uruchamia sprawdzenia`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        val vm = viewModel(w, "k1")
        val before = w.source.transactions.attempts
        w.source.transactions.failBeforeCommit = denied()

        vm.cancel("t1")
        advanceUntilIdle()

        assertEquals(TransferState.Failed("Nie udało się odwołać wizyty."), vm.transferState.value)
        assertEquals(1, w.source.transactions.attempts - before)
    }

    @Test
    fun `ponowne odwolanie po utracie odpowiedzi nie zwraca monet drugi raz`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        val before = w.coinsInSystem()
        val vm = viewModel(w, "k1")
        w.source.transactions.loseResponseAfterCommit = unavailable()
        vm.cancel("t1")
        advanceUntilIdle()
        vm.resetTransferState()

        vm.cancel("t1")
        advanceUntilIdle()

        assertTrue(vm.transferState.value is TransferState.Failed)
        assertEquals(1, ledgerCount(w, "t1_refund"))
        assertEquals(200, w.balance("k1"))
        assertEquals(before, w.coinsInSystem())
    }

    @Test
    fun `odzysk po oknie doszedl, odpowiedz nie wrocila - pacjent widzi odzyskane monety`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        w.now = START + 3 * DAY + 14 * DAY + 1
        val before = w.coinsInSystem()
        val vm = viewModel(w, "k1")
        val commitsBefore = w.source.transactions.commits
        w.source.transactions.loseResponseAfterCommit = unavailable()

        vm.reclaim("t1")
        advanceUntilIdle()

        assertEquals("odzysk musiał dojść do bazy", commitsBefore + 1, w.source.transactions.commits)
        assertEquals(TransferState.Reclaimed(CancelOutcome.Refunded(80)), vm.transferState.value)
        assertEquals(EscrowModel.REFUNDED, w.escrow("t1")?.status)
        assertEquals(200, w.balance("k1"))
        assertEquals(1, ledgerCount(w, "t1_refund"))
        assertEquals(before, w.coinsInSystem())
    }

    @Test
    fun `odzysk, ktory nie doszedl - porazka i depozyt nadal HELD`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        w.now = START + 3 * DAY + 14 * DAY + 1
        val vm = viewModel(w, "k1")
        w.source.transactions.failBeforeCommit = unavailable()

        vm.reclaim("t1")
        advanceUntilIdle()

        assertEquals(TransferState.Failed(UserFacingError.NO_CONNECTION), vm.transferState.value)
        assertEquals(EscrowModel.HELD, w.escrow("t1")?.status)
        assertEquals(120, w.balance("k1"))
    }

    @Test
    fun `odzysk doszedl, a sprawdzenie zawiodlo - niewiedza`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        w.now = START + 3 * DAY + 14 * DAY + 1
        val vm = viewModel(w, "k1")
        w.source.transactions.loseResponseAfterCommit = unavailable()
        w.source.transactions.readFailureAfterLostResponse = unavailable()

        vm.reclaim("t1")
        advanceUntilIdle()

        assertEquals(TransferState.Failed(UncertainWriteMessages.RECLAIM), vm.transferState.value)
        assertEquals(EscrowModel.REFUNDED, w.escrow("t1")?.status)
    }

    @Test
    fun `odzysk przed uplywem okna - komunikat domenowy bez zmian i bez sprawdzenia`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        val vm = viewModel(w, "k1")
        val before = w.source.transactions.attempts

        vm.reclaim("t1")
        advanceUntilIdle()

        val failed = vm.transferState.value as TransferState.Failed
        assertEquals("Wizyta jeszcze nie przepadła, a psycholog może ją oznaczyć jako odbytą.", failed.message)
        assertEquals(1, w.source.transactions.attempts - before)
    }

    @Test
    fun `zakonczenie wizyty doszlo, odpowiedz nie wrocila - psycholog widzi wyplate, nie napis o braku ruchu monet`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        w.now = START + 3 * DAY + HOUR
        val before = w.coinsInSystem()
        val vm = viewModel(w, "p1")
        val commitsBefore = w.source.transactions.commits
        w.source.transactions.loseResponseAfterCommit = unavailable()

        vm.complete("t1")
        advanceUntilIdle()

        assertEquals("zakończenie musiało dojść do bazy", commitsBefore + 1, w.source.transactions.commits)
        assertEquals(TransferState.Completed(CompletionOutcome.Released(80)), vm.transferState.value)
        assertEquals(EscrowModel.RELEASED, w.escrow("t1")?.status)
        assertEquals(130, w.balance("p1"))
        assertEquals(1, ledgerCount(w, "t1_p1"))
        assertEquals(before, w.coinsInSystem())
    }

    @Test
    fun `zakonczenie wizyty, ktore nie doszlo - porazka, depozyt HELD, wizyta BOOKED`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        w.now = START + 3 * DAY + HOUR
        val vm = viewModel(w, "p1")
        w.source.transactions.failBeforeCommit = unavailable()

        vm.complete("t1")
        advanceUntilIdle()

        assertEquals(TransferState.Failed(UserFacingError.NO_CONNECTION), vm.transferState.value)
        assertEquals("BOOKED", w.appointment("t1").status)
        assertEquals(EscrowModel.HELD, w.escrow("t1")?.status)
        assertEquals(50, w.balance("p1"))
    }

    @Test
    fun `zakonczenie doszlo, a sprawdzenie zawiodlo - niewiedza`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        w.now = START + 3 * DAY + HOUR
        val vm = viewModel(w, "p1")
        w.source.transactions.loseResponseAfterCommit = unavailable()
        w.source.transactions.readFailureAfterLostResponse = unavailable()

        vm.complete("t1")
        advanceUntilIdle()

        assertEquals(TransferState.Failed(UncertainWriteMessages.COMPLETION), vm.transferState.value)
        assertEquals("COMPLETED", w.appointment("t1").status)
    }

    @Test
    fun `ponowne zakonczenie po utracie odpowiedzi nie placi drugi raz i nie zaprzecza wyplacie`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        w.now = START + 3 * DAY + HOUR
        val before = w.coinsInSystem()
        val vm = viewModel(w, "p1")
        w.source.transactions.loseResponseAfterCommit = unavailable()
        w.source.transactions.readFailureAfterLostResponse = unavailable()
        vm.complete("t1")
        advanceUntilIdle()
        w.source.transactions.readOnlyFailure = null
        w.source.transactions.readFailureAfterLostResponse = null

        vm.complete("t1")
        advanceUntilIdle()

        assertEquals(TransferState.Completed(CompletionOutcome.Released(80)), vm.transferState.value)
        assertEquals(130, w.balance("p1"))
        assertEquals(1, ledgerCount(w, "t1_p1"))
        assertEquals(before, w.coinsInSystem())
    }

    @Test
    fun `zakonczenie wizyty bez depozytu po utracie odpowiedzi - sukces bez ruchu monet`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 0))
        w.bookDirectly()
        w.now = START + 3 * DAY + HOUR
        val vm = viewModel(w, "p1")
        val commitsBefore = w.source.transactions.commits
        w.source.transactions.loseResponseAfterCommit = unavailable()

        vm.complete("t1")
        advanceUntilIdle()

        assertTrue("zakończenie musiało dojść do bazy", w.source.transactions.commits > commitsBefore)
        assertEquals(TransferState.Completed(CompletionOutcome.NoCoinsMoved), vm.transferState.value)
        assertEquals("COMPLETED", w.appointment("t1").status)
    }

    @Test
    fun `odmowa regul przy zakonczeniu nie dodaje zadnego odczytu ponad to, co robi sam zapis`() = runTest(dispatcher) {
        val reference = world(slot("t1", "p1", 80))
        reference.bookDirectly()
        reference.now = START + 3 * DAY + HOUR
        reference.source.transactions.failBeforeCommit = denied()
        val referenceStart = reference.source.transactions.attempts
        runCatching { reference.source.completeAppointment("t1") }
        val referenceAttempts = reference.source.transactions.attempts - referenceStart
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        w.now = START + 3 * DAY + HOUR
        val vm = viewModel(w, "p1")
        val start = w.source.transactions.attempts
        w.source.transactions.failBeforeCommit = denied()

        vm.complete("t1")
        advanceUntilIdle()

        assertEquals(TransferState.Failed("Nie udało się zakończyć wizyty."), vm.transferState.value)
        assertEquals(
            "punkt odniesienia: samo zakończenie z odmową robi $referenceAttempts transakcji",
            referenceAttempts,
            w.source.transactions.attempts - start
        )
        assertTrue("punkt odniesienia musi coś mierzyć", referenceAttempts >= 1)
    }

    @Test
    fun `rozstrzygajace artefakty sa scisle - cudza rezerwacja, wolny termin i brak depozytu nie licza sie jako sukces`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80), slot("t2", "p1", 80, hour = 11), slot("t3", "p1", 0, hour = 12))
        w.bookDirectly("t1", "k2")
        w.bookDirectly("t3", "k1")

        assertFalse("wolny termin", w.source.bookingLanded("t2", "k1"))
        assertFalse("rezerwacja cudza", w.source.bookingLanded("t1", "k1"))
        assertTrue("rezerwacja własna z depozytem", w.source.bookingLanded("t1", "k2"))
        assertTrue("darmowy termin zarezerwowany przez siebie", w.source.bookingLanded("t3", "k1"))
        assertFalse("darmowy termin zarezerwowany przez kogo innego", w.source.bookingLanded("t3", "k2"))
        assertFalse("brak wizyty", w.source.bookingLanded("brak", "k1"))
        w.source.escrows.value = emptyMap()
        assertFalse("płatna rezerwacja bez depozytu nie dowodzi, że monety poszły", w.source.bookingLanded("t1", "k2"))
        assertNull("niezakończona wizyta", w.source.completionLanded("t1"))
        assertNull("niezwrócony depozyt", w.source.reclaimLanded("t1"))
        assertNull("nieodwołana wizyta", w.source.cancellationLanded("t1", "k2"))
    }

    @Test
    fun `odwolanie liczy sie tylko temu, kto je wykonal`() = runTest(dispatcher) {
        val w = world(slot("t1", "p1", 80))
        w.bookDirectly()
        w.source.cancelBooking("t1", "p1")

        assertEquals(CancelOutcome.Refunded(80), w.source.cancellationLanded("t1", "p1"))
        assertNull("odwołała druga strona, więc k1 nie może uznać tego za swój zapis", w.source.cancellationLanded("t1", "k1"))
    }
}
