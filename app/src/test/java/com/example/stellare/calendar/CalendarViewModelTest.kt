package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAppointmentDataSource
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeUserDataSource
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val wolnyTermin = AppointmentModel(
        appointmentId = "t1", psychologistId = "p1", patientId = null,
        date = "2026-10-01", time = "10:00", status = "FREE", priceCoins = 80
    )

    @Test
    fun `zajety termin ustawia komunikat bledu`() = runTest(dispatcher) {
        val source = FakeAppointmentDataSource(listOf(wolnyTermin.copy(status = "BOOKED")))
        val vm = CalendarViewModel(AppointmentRepository(source), fakeUserRepository("k1"))

        vm.book(wolnyTermin.copy(status = "BOOKED"), notes = "")
        advanceUntilIdle()

        val stan = vm.bookingState.value
        assertTrue(stan is BookingState.Error)
        assertEquals("Termin został już zarezerwowany.", (stan as BookingState.Error).message)
    }

    @Test
    fun `udana rezerwacja nie ustawia bledu`() = runTest(dispatcher) {
        val source = FakeAppointmentDataSource(listOf(wolnyTermin))
        val vm = CalendarViewModel(AppointmentRepository(source), fakeUserRepository("k1"))

        vm.book(wolnyTermin, notes = "pierwsza sesja")
        advanceUntilIdle()

        assertEquals(BookingState.Success, vm.bookingState.value)
        assertEquals("BOOKED", source.appointments.value.first().status)
    }

    @Test
    fun `reset stanu rezerwacji dziala`() = runTest(dispatcher) {
        val source = FakeAppointmentDataSource(listOf(wolnyTermin.copy(status = "BOOKED")))
        val vm = CalendarViewModel(AppointmentRepository(source), fakeUserRepository("k1"))

        vm.book(wolnyTermin.copy(status = "BOOKED"), notes = "")
        advanceUntilIdle()
        vm.resetBookingState()

        assertEquals(BookingState.Idle, vm.bookingState.value)
    }

    @Test
    fun `rezerwacja bez zalogowanego uzytkownika ustawia blad`() = runTest(dispatcher) {
        val source = FakeAppointmentDataSource(listOf(wolnyTermin))
        val niezalogowany = UserRepository(
            FakeAuthDataSource(),
            FakeUserDataSource(),
            FakeFavoriteDataSource()
        )
        val vm = CalendarViewModel(AppointmentRepository(source), niezalogowany)

        vm.book(wolnyTermin, notes = "")
        advanceUntilIdle()

        val stan = vm.bookingState.value
        assertTrue(stan is BookingState.Error)
        assertEquals("Brak zalogowanego użytkownika.", (stan as BookingState.Error).message)
    }

    @Test
    fun `rezerwacja terminu bez ustalonej ceny ustawia blad`() = runTest(dispatcher) {
        val termin = wolnyTermin.copy(priceCoins = 0)
        val source = FakeAppointmentDataSource(listOf(termin))
        val vm = CalendarViewModel(AppointmentRepository(source), fakeUserRepository("k1"))

        vm.book(termin, notes = "")
        advanceUntilIdle()

        val stan = vm.bookingState.value
        assertTrue(stan is BookingState.Error)
        assertEquals(
            "Termin nie ma ustalonej ceny. Poproś psychologa o wystawienie go ponownie.",
            (stan as BookingState.Error).message
        )
    }
}
