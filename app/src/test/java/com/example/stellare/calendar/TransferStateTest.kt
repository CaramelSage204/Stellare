package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.CompletionOutcome
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.fakes.FakeAppointmentDataSource
import com.example.stellare.fakes.FakeEscrowDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.fakes.fakeUserRepository
import com.example.stellare.ui.calendar.CalendarViewModel
import com.example.stellare.ui.calendar.TransferState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class TransferStateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val day = 24L * 60 * 60 * 1000
    private val booked = AppointmentModel(
        appointmentId = "t1", psychologistId = "p1", patientId = "k1",
        status = "BOOKED", priceCoins = 80, startsAtMillis = System.currentTimeMillis() + 3 * day
    )

    private fun viewModel(psychologistBalance: Int, appointment: AppointmentModel, uid: String): CalendarViewModel {
        val users = FakeUserDataSource(
            listOf(UserModel(id = "p1"), UserModel(id = "k1")),
            balances = mapOf("p1" to psychologistBalance, "k1" to 100)
        )
        val source = FakeAppointmentDataSource(listOf(appointment), users, FakeWalletDataSource())
        if (appointment.status == "BOOKED") source.seedDeposit(appointment.appointmentId)
        return CalendarViewModel(
            AppointmentRepository(source, FakeEscrowDataSource(source)),
            fakeUserRepository(uid, coinsBalance = checkNotNull(users.balanceOf(uid)), userDataSource = users),
            { TimeZone.getTimeZone("Europe/Warsaw") }
        )
    }

    @Test
    fun `stan poczatkowy to Idle`() = runTest(dispatcher) {
        assertEquals(TransferState.Idle, viewModel(200, booked, "k1").transferState.value)
    }

    @Test
    fun `odwolanie przez pacjenta ponad 24 h konczy sie stanem Cancelled z Refunded`() = runTest(dispatcher) {
        val vm = viewModel(200, booked, "k1")
        vm.cancel("t1")
        advanceUntilIdle()
        assertEquals(TransferState.Cancelled(CancelOutcome.Refunded(80)), vm.transferState.value)
    }

    @Test
    fun `odwolanie przez psychologa konczy sie stanem Cancelled z Refunded`() = runTest(dispatcher) {
        val vm = viewModel(200, booked, "p1")
        vm.cancel("t1")
        advanceUntilIdle()
        assertEquals(TransferState.Cancelled(CancelOutcome.Refunded(80)), vm.transferState.value)
    }

    @Test
    fun `odwolanie wizyty, ktora juz nie jest zarezerwowana, daje Failed z trescia bledu zrodla`() = runTest(dispatcher) {
        val vm = viewModel(200, booked.copy(status = "CANCELLED"), "p1")
        vm.cancel("t1")
        advanceUntilIdle()
        val state = vm.transferState.value
        assertTrue(state is TransferState.Failed)
        assertTrue((state as TransferState.Failed).message.isNotBlank())
    }

    @Test
    fun `resetTransferState wraca do Idle`() = runTest(dispatcher) {
        val vm = viewModel(200, booked, "k1")
        vm.cancel("t1")
        advanceUntilIdle()
        vm.resetTransferState()
        assertEquals(TransferState.Idle, vm.transferState.value)
    }

    @Test
    fun `stan pracy blokuje drugie wywolanie`() = runTest(dispatcher) {
        val vm = viewModel(200, booked, "k1")
        vm.cancel("t1")
        vm.cancel("t1")
        assertEquals(TransferState.InProgress, vm.transferState.value)
        advanceUntilIdle()
        assertEquals(TransferState.Cancelled(CancelOutcome.Refunded(80)), vm.transferState.value)
    }

    @Test
    fun `psycholog konczy wizyte - stan Completed niesie wyplate z depozytu`() = runTest(dispatcher) {
        val vm = viewModel(200, booked.copy(startsAtMillis = 1_000L), "p1")
        vm.complete("t1")
        advanceUntilIdle()
        assertEquals(TransferState.Completed(CompletionOutcome.Released(80)), vm.transferState.value)
    }

    @Test
    fun `zakonczenie wizyty, gdy pacjent juz odzyskal monety, mowi ze nic nie przeniesiono`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(UserModel(id = "p1"), UserModel(id = "k1")), balances = mapOf("p1" to 200, "k1" to 100))
        val source = FakeAppointmentDataSource(listOf(booked.copy(startsAtMillis = 1_000L)), users, FakeWalletDataSource())
        source.seedDeposit("t1")
        source.escrows.value = source.escrows.value.mapValues { it.value.copy(status = EscrowModel.REFUNDED) }
        val vm = CalendarViewModel(
            AppointmentRepository(source, FakeEscrowDataSource(source)),
            fakeUserRepository("p1", coinsBalance = 200, userDataSource = users),
            { TimeZone.getTimeZone("Europe/Warsaw") }
        )

        vm.complete("t1")
        advanceUntilIdle()

        assertEquals(TransferState.Completed(CompletionOutcome.NoCoinsMoved), vm.transferState.value)
        assertEquals(200, users.balanceOf("p1"))
    }

    @Test
    fun `nieudane zakonczenie wizyty jest widoczne jako Failed, a nie polkniete`() = runTest(dispatcher) {
        val vm = viewModel(200, booked, "p1")
        vm.complete("nie-ma-takiej")
        advanceUntilIdle()
        assertTrue("oczekiwano Failed, jest ${vm.transferState.value}", vm.transferState.value is TransferState.Failed)
    }
}
