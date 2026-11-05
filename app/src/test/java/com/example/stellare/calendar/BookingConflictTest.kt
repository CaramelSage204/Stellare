package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.BookingConflict
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.fakes.FakeAppointmentDataSource
import com.example.stellare.fakes.FakeEscrowDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingConflictTest {

    @Test
    fun `wolny termin pozwala rezerwowac`() {
        assertEquals("wolny termin ma przejść do rezerwacji", BookingConflict.Free, BookingConflict.classify("FREE", null, "k1"))
    }

    @Test
    fun `termin zajety przez ten sam podmiot to jego wlasna rezerwacja, nie konflikt`() {
        assertEquals(
            "rezerwacja zrobiona przez nas (np. ponowiona transakcja) nie może być zgłoszona jako cudza — pieniądze już przeszły",
            BookingConflict.BookedByRequester,
            BookingConflict.classify("BOOKED", "k1", "k1")
        )
    }

    @Test
    fun `termin zajety przez kogos innego to konflikt`() {
        assertEquals(
            "termin zajęty przez innego pacjenta ma być odrzucony",
            BookingConflict.TakenByOther,
            BookingConflict.classify("BOOKED", "k2", "k1")
        )
    }

    @Test
    fun `termin odwolany lub zakonczony z moim identyfikatorem nie jest moja aktywna rezerwacja`() {
        assertEquals(
            "tylko status BOOKED liczy się jako własna rezerwacja",
            BookingConflict.TakenByOther,
            BookingConflict.classify("CANCELLED", "k1", "k1")
        )
    }

    @Test
    fun `brak terminu to konflikt`() {
        assertEquals("nieistniejący termin nie może być rezerwowany", BookingConflict.TakenByOther, BookingConflict.classify(null, null, "k1"))
    }

    @Test
    fun `ponowna rezerwacja wlasnego terminu konczy sie sukcesem i nie pobiera monet drugi raz`() = runTest {
        val users = FakeUserDataSource(
            listOf(
                UserModel(id = "k1", role = UserRole.PATIENT),
                UserModel(id = "p1", role = UserRole.PSYCHOLOGIST)
            ),
            balances = mapOf("k1" to 200, "p1" to 0)
        )
        val source = FakeAppointmentDataSource(
            listOf(AppointmentModel(appointmentId = "t1", psychologistId = "p1", status = "FREE", priceCoins = 80, startsAtMillis = 4102444800000L)),
            users,
            FakeWalletDataSource()
        )
        val repo = AppointmentRepository(source, FakeEscrowDataSource(source))

        repo.bookAppointment("t1", "k1", "p1", 80, "")
        repo.bookAppointment("t1", "k1", "p1", 80, "")

        assertEquals("druga próba własnego terminu nie może pobrać monet ponownie", 120, users.balanceOf("k1"))
    }
}
