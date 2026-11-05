package com.example.stellare.calendar

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.remote.UserDataSource
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAppointmentDataSource
import com.example.stellare.fakes.FakeEscrowDataSource
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.FakeWalletDataSource
import com.example.stellare.ui.calendar.CalendarViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class ParticipantNamesTest {

    private val dispatcher = StandardTestDispatcher()
    private val strefa = { TimeZone.getTimeZone("Europe/Warsaw") }
    private val teraz = 1_790_000_000_000L

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class CountingUserDataSource(private val inner: FakeUserDataSource) : UserDataSource by inner {
        val readIds = mutableListOf<String>()

        override suspend fun getUserOnce(uid: String): UserModel? {
            readIds.add(uid)
            return inner.getUserOnce(uid)
        }
    }

    private class Setup(
        val vm: CalendarViewModel,
        val users: FakeUserDataSource,
        val reads: CountingUserDataSource,
        val appointments: FakeAppointmentDataSource,
        val auth: FakeAuthDataSource
    )

    private fun termin(id: String, psychologistId: String, patientId: String?, offsetHours: Int = 1) = AppointmentModel(
        appointmentId = id, psychologistId = psychologistId, patientId = patientId,
        startsAtMillis = teraz + offsetHours * 3_600_000L, status = if (patientId == null) "FREE" else "BOOKED", priceCoins = 80
    )

    private fun zbuduj(
        signedInAs: String?,
        documents: List<UserModel>,
        appointments: List<AppointmentModel> = emptyList()
    ): Setup {
        val users = FakeUserDataSource(documents)
        val reads = CountingUserDataSource(users)
        val auth = FakeAuthDataSource(signedInAs)
        val source = FakeAppointmentDataSource(appointments, users, FakeWalletDataSource())
        val vm = CalendarViewModel(
            AppointmentRepository(source, FakeEscrowDataSource(source)),
            UserRepository(auth, reads, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(reads)),
            strefa,
            { teraz }
        )
        return Setup(vm, users, reads, source, auth)
    }

    private fun TestScope.obserwuj(vm: CalendarViewModel) {
        backgroundScope.launch { vm.psychologistAppointments.collect {} }
        backgroundScope.launch { vm.patientAppointments.collect {} }
        backgroundScope.launch { vm.participantNames.collect {} }
        runCurrent()
    }

    private val psycholog = UserModel(id = "p1", firstName = "Anna", lastName = "Nowak", role = UserRole.PSYCHOLOGIST)
    private val pacjent = UserModel(id = "k1", firstName = "Jan", lastName = "Kowalski", role = UserRole.PATIENT)

    @Test
    fun `dwa terminy z tym samym pacjentem to jeden odczyt a kolejny termin nie dokłada odczytu`() = runTest(dispatcher) {
        val s = zbuduj("p1", listOf(psycholog, pacjent), listOf(termin("t1", "p1", "k1", 1), termin("t2", "p1", "k1", 3)))
        obserwuj(s.vm)

        assertEquals(listOf("k1"), s.reads.readIds)

        s.appointments.appointments.value = s.appointments.appointments.value + termin("t3", "p1", "k1", 5)
        runCurrent()

        assertEquals(listOf("k1"), s.reads.readIds)
    }

    @Test
    fun `psycholog widzi nazwe pacjenta`() = runTest(dispatcher) {
        val s = zbuduj("p1", listOf(psycholog, pacjent), listOf(termin("t1", "p1", "k1")))
        obserwuj(s.vm)

        assertEquals(mapOf("k1" to "Jan Kowalski"), s.vm.participantNames.value)
    }

    @Test
    fun `pacjent widzi nazwe psychologa`() = runTest(dispatcher) {
        val s = zbuduj("k1", listOf(psycholog, pacjent), listOf(termin("t1", "p1", "k1")))
        obserwuj(s.vm)

        assertEquals(mapOf("p1" to "Anna Nowak"), s.vm.participantNames.value)
    }

    @Test
    fun `wolny termin bez pacjenta nie powoduje odczytu`() = runTest(dispatcher) {
        val s = zbuduj("p1", listOf(psycholog), listOf(termin("t1", "p1", null)))
        obserwuj(s.vm)

        assertEquals(emptyList<String>(), s.reads.readIds)
        assertEquals(emptyMap<String, String>(), s.vm.participantNames.value)
    }

    @Test
    fun `niezalogowany nie wykonuje zadnego odczytu uzytkownika`() = runTest(dispatcher) {
        val s = zbuduj(null, listOf(psycholog, pacjent), listOf(termin("t1", "p1", "k1")))
        obserwuj(s.vm)

        assertEquals(emptyList<String>(), s.reads.readIds)
        assertEquals(emptyMap<String, String>(), s.vm.participantNames.value)
    }

    @Test
    fun `wylogowanie czysci nazwy uczestnikow`() = runTest(dispatcher) {
        val s = zbuduj("p1", listOf(psycholog, pacjent), listOf(termin("t1", "p1", "k1")))
        obserwuj(s.vm)

        s.auth.signOut()
        runCurrent()

        assertEquals(emptyMap<String, String>(), s.vm.participantNames.value)
    }

    @Test
    fun `zalogowany bez dokumentu uzytkownika ma nazwy po zapisaniu dokumentu bez ponownego logowania`() = runTest(dispatcher) {
        val s = zbuduj("k1", listOf(psycholog), listOf(termin("t1", "p1", "k1")))
        obserwuj(s.vm)

        assertEquals(emptyList<String>(), s.reads.readIds)

        s.users.saveUser(pacjent)
        runCurrent()

        assertEquals(mapOf("p1" to "Anna Nowak"), s.vm.participantNames.value)
        assertEquals(listOf("p1"), s.reads.readIds)
    }

    @Test
    fun `brakujacy dokument nie jest zapamietany jako brak nazwy`() = runTest(dispatcher) {
        val s = zbuduj("p1", listOf(psycholog), listOf(termin("t1", "p1", "k1")))
        obserwuj(s.vm)

        assertEquals(emptyMap<String, String>(), s.vm.participantNames.value)

        s.users.saveUser(pacjent)
        s.appointments.appointments.value = s.appointments.appointments.value + termin("t2", "p1", "k1", 4)
        runCurrent()

        assertEquals(mapOf("k1" to "Jan Kowalski"), s.vm.participantNames.value)
    }

    @Test
    fun `wybrany dzien na poczatku to dzis`() = runTest(dispatcher) {
        val s = zbuduj("p1", listOf(psycholog))

        assertEquals(teraz, s.vm.selectedDayMillis.value)
    }

    @Test
    fun `wybor dnia z innego tygodnia jest zapamietany`() = runTest(dispatcher) {
        val s = zbuduj("p1", listOf(psycholog))
        val zaTydzien = teraz + 7 * 24 * 3_600_000L

        s.vm.selectDay(zaTydzien)

        assertEquals(zaTydzien, s.vm.selectedDayMillis.value)
    }
}
