package com.example.stellare.resilience

import com.example.stellare.ui.StaleData
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StaleDataTest {

    private val dispatcher = StandardTestDispatcher()

    private fun odmowa() =
        FirebaseFirestoreException("odmowa regul", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    @Test
    fun `zdrowy strumien nie gasi komunikatu o tym, ktory padl`() = runTest(dispatcher) {
        val stale = StaleData("Pokazane dane moga byc nieaktualne.")
        val padajacy = flow<Int> { throw odmowa() }

        backgroundScope.launch { stale.cover("T", "padajacy") { padajacy }.collect {} }
        runCurrent()
        backgroundScope.launch { stale.cover("T", "wygaszony rola") { flowOf(0) }.collect {} }
        runCurrent()

        assertNotNull(
            "strumien wygaszony rola emituje pusta liste od razu i nie wolno mu zgasic komunikatu " +
                "o strumieniu, ktory naprawde padl - inaczej ekran znow przedstawia nieaktualne dane jako aktualne",
            stale.message.value
        )
    }

    @Test
    fun `anulowanie zbierania zdejmuje wpis, zeby nie zostal baner bez przedmiotu`() = runTest(dispatcher) {
        val stale = StaleData("Pokazane dane moga byc nieaktualne.")
        val zimnyPadajacy = flow<Int> { throw odmowa() }

        val ekran = launch { stale.cover("T", "zimny per ekran") { zimnyPadajacy }.collect {} }
        runCurrent()
        assertNotNull("warunek wstepny: komunikat jest ustawiony", stale.message.value)

        ekran.cancel()
        runCurrent()

        assertNull(
            "po zamknieciu ekranu, ktorego strumien padl, komunikat musi zniknac: nikt go juz nie " +
                "zbiera, wiec ponowienie nie ma czego ponowic, a baner zostalby na wspoldzielonej " +
                "instancji do konca zycia modelu widoku",
            stale.message.value
        )
    }

    @Test
    fun `trwala odmowa nie gasi komunikatu samym zakonczeniem przeplywu`() = runTest(dispatcher) {
        val stale = StaleData("Pokazane dane moga byc nieaktualne.")
        val padajacy = flow<Int> { throw odmowa() }

        backgroundScope.launch { stale.cover("T", "padajacy") { padajacy }.collect {} }
        runCurrent()

        assertNotNull(
            "po odmowie trwalej retryWithBackoff konczy przeplyw normalnie; zdejmowanie wpisu przy " +
                "kazdym zakonczeniu zgasiloby komunikat ustawiony chwile wczesniej",
            stale.message.value
        )
    }

    @Test
    fun `komunikat gasnie, gdy kazdy pokryty strumien znow dostarcza dane`() = runTest(dispatcher) {
        val stale = StaleData("Pokazane dane moga byc nieaktualne.")
        var padaj = true
        val chwiejny = flow { if (padaj) throw odmowa() else emit(1) }

        backgroundScope.launch { stale.cover("T", "chwiejny") { chwiejny }.collect {} }
        runCurrent()
        assertNotNull("warunek wstepny: komunikat jest ustawiony", stale.message.value)

        padaj = false
        stale.retry()
        runCurrent()

        assertNull("gdy wszystkie pokryte strumienie dostarczaja dane, komunikat ma zgasnac", stale.message.value)
    }
}
