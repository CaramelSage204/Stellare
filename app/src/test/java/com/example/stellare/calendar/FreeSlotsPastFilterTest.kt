package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
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
import kotlinx.coroutines.launch
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
class FreeSlotsPastFilterTest {

    private val dispatcher = StandardTestDispatcher()
    private val strefa = { TimeZone.getTimeZone("Europe/Warsaw") }
    private val teraz = 1_800_000_000_000L

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun termin(id: String, startsAt: Long) = AppointmentModel(
        appointmentId = id, psychologistId = "p1", startsAtMillis = startsAt,
        date = "2027-01-15", time = "10:00", status = "FREE", priceCoins = 50
    )

    private fun viewModel(vararg terminy: AppointmentModel): CalendarViewModel {
        val users = FakeUserDataSource()
        val source = FakeAppointmentDataSource(terminy.toList(), users, FakeWalletDataSource())
        return CalendarViewModel(
            AppointmentRepository(source, FakeEscrowDataSource(source)),
            fakeUserRepository("k1", userDataSource = users),
            strefa,
            clock = { teraz }
        )
    }

    @Test
    fun `lista wolnych terminow nie pokazuje przeszlosci`() = runTest(dispatcher) {
        val vm = viewModel(termin("przeszly", teraz - 60_000), termin("przyszly", teraz + 60_000))
        val wynik = mutableListOf<String>()
        backgroundScope.launch { vm.freeSlotsFor("p1").collect { wynik.addAll(it.map { t -> t.appointmentId }) } }
        advanceUntilIdle()

        assertEquals(listOf("przyszly"), wynik)
    }

    @Test
    fun `termin rowno teraz jest juz przeszloscia`() = runTest(dispatcher) {
        val vm = viewModel(termin("teraz", teraz))
        val wynik = mutableListOf<String>()
        backgroundScope.launch { vm.freeSlotsFor("p1").collect { wynik.addAll(it.map { t -> t.appointmentId }) } }
        advanceUntilIdle()

        assertEquals(emptyList<String>(), wynik)
    }

    @Test
    fun `rezerwacja terminu z przeszlosci daje czytelny komunikat`() = runTest(dispatcher) {
        val przeszly = termin("przeszly", teraz - 60_000)
        val vm = viewModel(przeszly)

        vm.book(przeszly)
        advanceUntilIdle()

        val stan = vm.bookingState.value
        assertTrue(stan is BookingState.Error)
        assertEquals("Ten termin już minął. Wybierz inny.", (stan as BookingState.Error).message)
    }
}
