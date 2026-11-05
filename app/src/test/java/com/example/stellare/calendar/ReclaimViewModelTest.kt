package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.EscrowSettlement
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.data.remote.EscrowDataSource
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.fakes.FakeAppointmentDataSource
import com.example.stellare.fakes.FakeEscrowDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.fakes.fakeUserRepository
import com.example.stellare.ui.calendar.CalendarViewModel
import com.example.stellare.ui.calendar.DepositState
import com.example.stellare.ui.calendar.TransferState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
class ReclaimViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val window = EscrowSettlement.ABANDONED_BOOKING_MILLIS

    private class Swiat(val appointment: AppointmentModel, clock: () -> Long, seedDeposit: Boolean = true) {
        val users = FakeUserDataSource(listOf(UserModel(id = "p1"), UserModel(id = "k1")), balances = mapOf("p1" to 200, "k1" to 100))
        val wallet = FakeWalletDataSource()
        val source = FakeAppointmentDataSource(listOf(appointment), users, wallet, clock)
            .also { if (seedDeposit) it.seedDeposit(appointment.appointmentId) }

        fun balance(uid: String) = checkNotNull(users.balanceOf(uid))
        fun deposit() = source.escrows.value.getValue(appointment.appointmentId)
    }

    private fun wizyta(startsAgo: Long) = AppointmentModel(
        appointmentId = "t1", psychologistId = "p1", patientId = "k1",
        status = "BOOKED", priceCoins = 80, startsAtMillis = now - startsAgo
    )

    private fun viewModel(swiat: Swiat, uid: String, escrows: EscrowDataSource = FakeEscrowDataSource(swiat.source)) = CalendarViewModel(
        AppointmentRepository(swiat.source, escrows),
        fakeUserRepository(uid, coinsBalance = swiat.balance(uid), userDataSource = swiat.users),
        { TimeZone.getTimeZone("Europe/Warsaw") },
        { now }
    )

    @Test
    fun `pacjent po oknie 14 dni odzyskuje monety, a wizyta zostaje BOOKED`() = runTest(dispatcher) {
        val swiat = Swiat(wizyta(window + minute), { now })
        val vm = viewModel(swiat, "k1")

        vm.reclaim("t1")
        advanceUntilIdle()

        assertEquals(TransferState.Reclaimed(CancelOutcome.Refunded(80)), vm.transferState.value)
        assertEquals(180, swiat.balance("k1"))
        assertEquals(200, swiat.balance("p1"))
        assertEquals(EscrowModel.REFUNDED, swiat.deposit().status)
        assertEquals("BOOKED", swiat.source.appointments.value.single().status)
        assertEquals(WalletTransactionType.REFUND, swiat.wallet.transactions.value.single().type)
    }

    @Test
    fun `przed uplywem okna odzyskanie konczy sie Failed i nie rusza monet`() = runTest(dispatcher) {
        val swiat = Swiat(wizyta(window - minute), { now })
        val vm = viewModel(swiat, "k1")

        vm.reclaim("t1")
        advanceUntilIdle()

        assertTrue("oczekiwano Failed, jest ${vm.transferState.value}", vm.transferState.value is TransferState.Failed)
        assertEquals(100, swiat.balance("k1"))
        assertEquals(EscrowModel.HELD, swiat.deposit().status)
    }

    @Test
    fun `psycholog nie odzyska monet pacjenta`() = runTest(dispatcher) {
        val swiat = Swiat(wizyta(window + minute), { now })
        val vm = viewModel(swiat, "p1")

        vm.reclaim("t1")
        advanceUntilIdle()

        assertTrue(vm.transferState.value is TransferState.Failed)
        assertEquals(100, swiat.balance("k1"))
        assertEquals(EscrowModel.HELD, swiat.deposit().status)
    }

    @Test
    fun `podglad depozytu zna dokument, jego brak i blad odczytu`() = runTest(dispatcher) {
        val z = Swiat(wizyta(minute), { now })
        assertEquals(DepositState.Known(z.deposit()), viewModel(z, "k1").depositOf("t1").first())

        val bez = Swiat(wizyta(minute), { now }, seedDeposit = false)
        assertEquals(DepositState.Known(null), viewModel(bez, "k1").depositOf("t1").first())

        val awaria = object : EscrowDataSource {
            override fun observeEscrow(appointmentId: String): Flow<EscrowModel?> = flow { throw IllegalStateException("odmowa") }
        }
        assertEquals(DepositState.Unavailable, viewModel(z, "k1", awaria).depositOf("t1").first())
    }

    @Test
    fun `podglad depozytu odzwierciedla rozliczenie`() = runTest(dispatcher) {
        val swiat = Swiat(wizyta(window + minute), { now })
        val vm = viewModel(swiat, "k1")
        assertEquals(EscrowModel.HELD, (vm.depositOf("t1").first() as DepositState.Known).escrow?.status)

        vm.reclaim("t1")
        advanceUntilIdle()

        assertEquals(EscrowModel.REFUNDED, (vm.depositOf("t1").first() as DepositState.Known).escrow?.status)
    }
}
