package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.remote.AppointmentDataSource
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.fakes.FakeAppointmentDataSource
import com.example.stellare.fakes.FakeEscrowDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.fakes.fakeUserRepository
import com.example.stellare.ui.calendar.BookingState
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
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class BookingDoubleTapTest {

    private val dispatcher = StandardTestDispatcher()
    private val strefa = { TimeZone.getTimeZone("Europe/Warsaw") }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val termin = AppointmentModel(
        appointmentId = "t1", psychologistId = "p1", patientId = null,
        startsAtMillis = 4102444800000L, date = "2026-10-01", time = "10:00", status = "FREE", priceCoins = 80
    )

    private class CountingSource(val inner: FakeAppointmentDataSource) : AppointmentDataSource by inner {
        var bookCalls = 0
            private set

        override suspend fun bookAppointmentAtomically(
            appointmentId: String,
            patientId: String,
            psychologistId: String,
            priceCoins: Int,
            notes: String
        ) {
            bookCalls++
            inner.bookAppointmentAtomically(appointmentId, patientId, psychologistId, priceCoins, notes)
        }
    }

    @Test
    fun `drugie dotkniecie w trakcie rezerwacji nie wysyla drugiego zadania`() = runTest(dispatcher) {
        val users = FakeUserDataSource(
            listOf(
                UserModel(id = "k1", role = UserRole.PATIENT),
                UserModel(id = "p1", role = UserRole.PSYCHOLOGIST)
            ),
            balances = mapOf("k1" to 200, "p1" to 0)
        )
        val source = CountingSource(FakeAppointmentDataSource(listOf(termin), users, FakeWalletDataSource()))
        val vm = CalendarViewModel(AppointmentRepository(source, FakeEscrowDataSource(source.inner)), fakeUserRepository("k1", userDataSource = users), strefa)

        vm.book(termin, "")
        vm.book(termin, "")
        advanceUntilIdle()

        assertEquals("dwa szybkie dotknięcia mają wysłać jedno żądanie rezerwacji, nie dwa", 1, source.bookCalls)
        assertEquals(BookingState.Success, vm.bookingState.value)
    }

    @Test
    fun `po zakonczonej rezerwacji kolejne wywolanie znowu jest dozwolone`() = runTest(dispatcher) {
        val users = FakeUserDataSource(listOf(UserModel(id = "k1", role = UserRole.PATIENT)), balances = mapOf("k1" to 10))
        val source = CountingSource(FakeAppointmentDataSource(listOf(termin), users, FakeWalletDataSource()))
        val vm = CalendarViewModel(AppointmentRepository(source, FakeEscrowDataSource(source.inner)), fakeUserRepository("k1", userDataSource = users), strefa)

        vm.book(termin, "")
        advanceUntilIdle()
        vm.book(termin, "")
        advanceUntilIdle()

        assertEquals("po zakończeniu pierwszej próby (błąd braku monet) można spróbować ponownie", 2, source.bookCalls)
    }
}
