package com.example.stellare.calendar

import com.example.stellare.fakes.FakePrivateProfileDataSource
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.model.AppointmentSchedule
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAppointmentDataSource
import com.example.stellare.fakes.FakeEscrowDataSource
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val strefa = { TimeZone.getTimeZone("Europe/Warsaw") }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val wolnyTermin = AppointmentModel(
        appointmentId = "t1", psychologistId = "p1", patientId = null,
        startsAtMillis = 4102444800000L, date = "2026-10-01", time = "10:00", status = "FREE", priceCoins = 80
    )

    @Test
    fun `zajety termin ustawia komunikat bledu`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        val source = FakeAppointmentDataSource(listOf(wolnyTermin.copy(status = "BOOKED")), users, FakeWalletDataSource())
        val vm = CalendarViewModel(AppointmentRepository(source, FakeEscrowDataSource(source)), fakeUserRepository("k1", userDataSource = users), strefa)

        vm.book(wolnyTermin.copy(status = "BOOKED"), notes = "")
        advanceUntilIdle()

        val stan = vm.bookingState.value
        assertTrue(stan is BookingState.Error)
        assertEquals("Termin został już zarezerwowany.", (stan as BookingState.Error).message)
    }

    @Test
    fun `udana rezerwacja nie ustawia bledu`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        val source = FakeAppointmentDataSource(listOf(wolnyTermin), users, FakeWalletDataSource())
        val vm = CalendarViewModel(AppointmentRepository(source, FakeEscrowDataSource(source)), fakeUserRepository("k1", userDataSource = users), strefa)

        vm.book(wolnyTermin, notes = "pierwsza sesja")
        advanceUntilIdle()

        assertEquals(BookingState.Success, vm.bookingState.value)
        assertEquals("BOOKED", source.appointments.value.first().status)
    }

    @Test
    fun `reset stanu rezerwacji dziala`() = runTest(dispatcher) {
        val users = FakeUserDataSource()
        val source = FakeAppointmentDataSource(listOf(wolnyTermin.copy(status = "BOOKED")), users, FakeWalletDataSource())
        val vm = CalendarViewModel(AppointmentRepository(source, FakeEscrowDataSource(source)), fakeUserRepository("k1", userDataSource = users), strefa)

        vm.book(wolnyTermin.copy(status = "BOOKED"), notes = "")
        advanceUntilIdle()
        vm.resetBookingState()

        assertEquals(BookingState.Idle, vm.bookingState.value)
    }

    @Test
    fun `rezerwacja bez zalogowanego uzytkownika ustawia blad`() = runTest(dispatcher) {
        val source = FakeAppointmentDataSource(listOf(wolnyTermin), FakeUserDataSource(), FakeWalletDataSource())
        val niezalogowany = UserRepository(
            FakeAuthDataSource(),
            FakeUserDataSource(),
            FakeFavoriteDataSource(),
            FakeRatingDataSource(),
            FakePrivateProfileDataSource()
        )
        val vm = CalendarViewModel(AppointmentRepository(source, FakeEscrowDataSource(source)), niezalogowany, strefa)

        vm.book(wolnyTermin, notes = "")
        advanceUntilIdle()

        val stan = vm.bookingState.value
        assertTrue(stan is BookingState.Error)
        assertEquals("Brak zalogowanego użytkownika.", (stan as BookingState.Error).message)
    }

    @Test
    fun `rezerwacja terminu bez ustalonej ceny ustawia blad`() = runTest(dispatcher) {
        val termin = wolnyTermin.copy(priceCoins = 0)
        val users = FakeUserDataSource()
        val source = FakeAppointmentDataSource(listOf(termin), users, FakeWalletDataSource())
        val vm = CalendarViewModel(AppointmentRepository(source, FakeEscrowDataSource(source)), fakeUserRepository("k1", userDataSource = users), strefa)

        vm.book(termin, notes = "")
        advanceUntilIdle()

        val stan = vm.bookingState.value
        assertTrue(stan is BookingState.Error)
        assertEquals(
            "Termin nie ma ustalonej ceny. Poproś psychologa o wystawienie go ponownie.",
            (stan as BookingState.Error).message
        )
    }

    @Test
    fun `dwie odbyte wizyty tej samej pary koncza sie oba i zostawiaja jeden znacznik`() = runTest(dispatcher) {
        val booked = wolnyTermin.copy(patientId = "k1", status = "BOOKED", startsAtMillis = 1_000L)
        val users = FakeUserDataSource()
        val source = FakeAppointmentDataSource(listOf(booked, booked.copy(appointmentId = "t2", time = "11:00")), users, FakeWalletDataSource())
        val vm = CalendarViewModel(AppointmentRepository(source, FakeEscrowDataSource(source)), fakeUserRepository("p1", userDataSource = users), strefa)

        vm.complete("t1")
        vm.complete("t2")
        advanceUntilIdle()

        assertEquals(listOf("COMPLETED", "COMPLETED"), source.appointments.value.map { it.status })
        assertEquals(setOf("p1_k1"), source.completions.value.keys)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.wystaw(start: Long, cena: Double): AppointmentModel {
        val users = FakeUserDataSource()
        val repo = fakeUserRepository("p1", userDataSource = users)
        users.users.value = users.users.value.map { it.copy(pricePerSession = cena, role = UserRole.PSYCHOLOGIST) }
        val source = FakeAppointmentDataSource(users = users, wallet = FakeWalletDataSource(), clock = { start - 24L * 60 * 60 * 1000 })
        val vm = CalendarViewModel(AppointmentRepository(source, FakeEscrowDataSource(source)), repo, strefa)
        source.signedInUserId = "p1"
        vm.addFreeSlot(start)
        advanceUntilIdle()
        check(source.slotDenials.isEmpty()) { "przygotowanie testu: reguły odrzuciły wystawienie terminu: ${source.slotDenials}" }
        return source.appointments.value.single()
    }

    private val start = 1_791_000_000_000L

    @Test
    fun `wystawiony termin zapisuje startsAtMillis`() = runTest(dispatcher) {
        assertEquals(start, wystaw(start, 95.0).startsAtMillis)
    }

    @Test
    fun `wystawiony termin ma date wyprowadzona z liczby`() = runTest(dispatcher) {
        assertEquals(AppointmentSchedule.dateOf(start, strefa()), wystaw(start, 95.0).date)
    }

    @Test
    fun `wystawiony termin ma godzine wyprowadzona z liczby`() = runTest(dispatcher) {
        assertEquals(AppointmentSchedule.timeOf(start, strefa()), wystaw(start, 95.0).time)
    }

    @Test
    fun `wystawiony termin bierze cene z profilu`() = runTest(dispatcher) {
        assertEquals(95, wystaw(start, 95.0).priceCoins)
    }

    @Test
    fun `wystawiony termin obcina ulamkowa cene profilu`() = runTest(dispatcher) {
        assertEquals(80, wystaw(start, 80.5).priceCoins)
    }

    @Test
    fun `wystawiony termin jest wolny`() = runTest(dispatcher) {
        assertEquals("FREE", wystaw(start, 95.0).status)
    }

    @Test
    fun `wystawiony termin nalezy do zalogowanego psychologa`() = runTest(dispatcher) {
        assertEquals("p1", wystaw(start, 95.0).psychologistId)
    }
}
