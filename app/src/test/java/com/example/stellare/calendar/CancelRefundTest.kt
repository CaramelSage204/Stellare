package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.fakes.FakeAppointmentDataSource
import com.example.stellare.fakes.FakeEscrowDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.fakes.fakeUserRepository
import com.example.stellare.ui.calendar.CalendarViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class CancelRefundTest {

    private val dispatcher = StandardTestDispatcher()
    private val strefa = { TimeZone.getTimeZone("Europe/Warsaw") }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val dzien = 24L * 60 * 60 * 1000
    private val teraz = System.currentTimeMillis()

    private val zarezerwowana = AppointmentModel(
        appointmentId = "t1", psychologistId = "p1", patientId = "k1",
        status = "BOOKED", priceCoins = 80, startsAtMillis = teraz + 3 * dzien
    )
    private val bliska = zarezerwowana.copy(startsAtMillis = teraz + 2 * 60 * 60 * 1000)

    private class Swiat(psychologistBalance: Int, val appointment: AppointmentModel) {
        val users = FakeUserDataSource(
            listOf(UserModel(id = "p1"), UserModel(id = "k1")),
            balances = mapOf("p1" to psychologistBalance, "k1" to 100)
        )
        val wallet = FakeWalletDataSource()
        val source = FakeAppointmentDataSource(listOf(appointment), users, wallet)
            .also { it.seedDeposit(appointment.appointmentId) }

        fun balance(uid: String) = checkNotNull(users.balanceOf(uid))
        fun deposit() = source.escrows.value.getValue(appointment.appointmentId)
    }

    private fun viewModel(swiat: Swiat, uid: String) = CalendarViewModel(
        AppointmentRepository(swiat.source, FakeEscrowDataSource(swiat.source)),
        fakeUserRepository(uid, coinsBalance = swiat.users.balanceOf(uid) ?: 0, userDataSource = swiat.users),
        strefa
    )

    @Test
    fun `psycholog odwoluje - pacjent dostaje cene z depozytu, psycholog nic nie traci, wizyta jest CANCELLED`() = runTest(dispatcher) {
        val swiat = Swiat(psychologistBalance = 200, appointment = zarezerwowana)
        val vm = viewModel(swiat, "p1")

        vm.cancel("t1")
        advanceUntilIdle()

        assertEquals(CancelOutcome.Refunded(80), vm.cancelOutcome.value)
        assertEquals(180, swiat.balance("k1"))
        assertEquals(200, swiat.balance("p1"))
        assertEquals("CANCELLED", swiat.source.appointments.value.single().status)
        assertEquals(EscrowModel.REFUNDED, swiat.deposit().status)
    }

    @Test
    fun `zwrot zapisuje wpis REFUND w ksiedze pacjenta`() = runTest(dispatcher) {
        val swiat = Swiat(psychologistBalance = 200, appointment = zarezerwowana)
        val vm = viewModel(swiat, "p1")

        vm.cancel("t1")
        advanceUntilIdle()

        val wpis = swiat.wallet.transactions.value.single()
        assertEquals("k1", wpis.userId)
        assertEquals(80, wpis.amount)
        assertEquals(WalletTransactionType.REFUND, wpis.type)
    }

    @Test
    fun `psycholog z pustym portfelem odwoluje wizyte, bo zwrot idzie z depozytu`() = runTest(dispatcher) {
        val swiat = Swiat(psychologistBalance = 0, appointment = zarezerwowana)
        val vm = viewModel(swiat, "p1")

        vm.cancel("t1")
        advanceUntilIdle()

        assertEquals(CancelOutcome.Refunded(80), vm.cancelOutcome.value)
        assertEquals("CANCELLED", swiat.source.appointments.value.single().status)
        assertEquals(180, swiat.balance("k1"))
        assertEquals(0, swiat.balance("p1"))
    }

    @Test
    fun `pacjent odwoluje ponizej 24 h - wizyta CANCELLED, monety trafiaja do psychologa`() = runTest(dispatcher) {
        val swiat = Swiat(psychologistBalance = 200, appointment = bliska)
        val vm = viewModel(swiat, "k1")

        vm.cancel("t1")
        advanceUntilIdle()

        assertEquals(CancelOutcome.Released(80), vm.cancelOutcome.value)
        assertEquals("CANCELLED", swiat.source.appointments.value.single().status)
        assertEquals(100, swiat.balance("k1"))
        assertEquals(280, swiat.balance("p1"))
        assertEquals(EscrowModel.RELEASED, swiat.deposit().status)
        val wpis = swiat.wallet.transactions.value.single()
        assertEquals("p1", wpis.userId)
        assertEquals(WalletTransactionType.BOOKING_EARNING, wpis.type)
    }

    @Test
    fun `osoba trzecia nie odwola cudzej wizyty`() = runTest(dispatcher) {
        val swiat = Swiat(psychologistBalance = 200, appointment = zarezerwowana)
        val vm = viewModel(swiat, "obcy")

        vm.cancel("t1")
        advanceUntilIdle()

        assertNull(vm.cancelOutcome.value)
        assertEquals("BOOKED", swiat.source.appointments.value.single().status)
        assertEquals(EscrowModel.HELD, swiat.deposit().status)
    }

    @Test
    fun `drugie odwolanie tej samej wizyty nie zwraca drugi raz`() = runTest(dispatcher) {
        val swiat = Swiat(psychologistBalance = 200, appointment = zarezerwowana)
        val vm = viewModel(swiat, "p1")

        vm.cancel("t1")
        advanceUntilIdle()
        vm.cancel("t1")
        advanceUntilIdle()

        assertEquals(180, swiat.balance("k1"))
        assertEquals(1, swiat.wallet.transactions.value.size)
    }

    @Test
    fun `pacjent odwoluje ponad 24 h przed terminem - zwrot od razu, bez czekania na psychologa`() = runTest(dispatcher) {
        val swiat = Swiat(psychologistBalance = 200, appointment = zarezerwowana)
        val vm = viewModel(swiat, "k1")

        vm.cancel("t1")
        advanceUntilIdle()

        assertEquals(CancelOutcome.Refunded(80), vm.cancelOutcome.value)
        assertEquals("CANCELLED", swiat.source.appointments.value.single().status)
        assertEquals(180, swiat.balance("k1"))
        assertEquals(200, swiat.balance("p1"))
        assertEquals(EscrowModel.REFUNDED, swiat.deposit().status)
    }

    @Test
    fun `wizyta sprzed depozytow jest odwolywana bez ruchu monet`() = runTest(dispatcher) {
        val swiat = Swiat(psychologistBalance = 200, appointment = zarezerwowana)
        swiat.source.escrows.value = emptyMap()
        val vm = viewModel(swiat, "p1")

        vm.cancel("t1")
        advanceUntilIdle()

        assertEquals(CancelOutcome.CancelledWithoutRefund, vm.cancelOutcome.value)
        assertEquals("CANCELLED", swiat.source.appointments.value.single().status)
        assertEquals(100, swiat.balance("k1"))
        assertEquals(200, swiat.balance("p1"))
    }
}
