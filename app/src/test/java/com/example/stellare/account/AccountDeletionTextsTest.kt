package com.example.stellare.account

import com.example.stellare.data.model.AccountDeletionResult
import com.example.stellare.data.model.DeletionProgress
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.UserFacingError
import com.example.stellare.ui.settings.AccountDeletionDisclosure
import com.example.stellare.ui.settings.AccountDeletionMessages
import com.google.firebase.auth.FirebaseAuthException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionTextsTest {

    private val halfway = AccountDeletionResult.Failed(
        DeletionProgress(
            notesDeleted = 1,
            notesTotal = 1,
            userDocumentDeleted = true,
            postsDeleted = 1,
            postsTotal = 3
        )
    )

    @Test
    fun `porazka bez skasowania czegokolwiek mowi, ze nic nie zniknelo i konto zostalo`() {
        val text = AccountDeletionMessages.failure(AccountDeletionResult.Failed(DeletionProgress()))

        assertTrue("komunikat ma mowic, ze konto nie zostalo usuniete: $text", text.contains("NIE zostało usunięte"))
        assertTrue("komunikat ma mowic, ze nic nie skasowano: $text", text.contains("nic nie zostało skasowane"))
    }

    @Test
    fun `porazka w polowie nie twierdzi, ze konto usunieto`() {
        val text = AccountDeletionMessages.failure(halfway)

        assertTrue("komunikat ma zaczynac od tego, ze konto istnieje: $text", text.startsWith("Konto NIE zostało usunięte"))
        assertFalse("komunikat nie moze mowic o sukcesie: $text", text.contains("Konto zostało usunięte"))
    }

    @Test
    fun `porazka w polowie wymienia, co usunieto i co zostalo`() {
        val text = AccountDeletionMessages.failure(halfway)

        assertTrue("brak listy usunietych elementow: $text", text.contains("Już usunięto: profil, ogłoszenia (1 z 3), notatki (1 z 1)"))
        assertTrue("brak listy pozostalych elementow: $text", text.contains("Nadal istnieje: konto logowania, 2 ogłoszenia"))
    }

    @Test
    fun `porazka w polowie zachecia do ponowienia`() {
        assertTrue(AccountDeletionMessages.failure(halfway).contains("ponów próbę"))
    }

    @Test
    fun `wymog logowania na koncu odsyla do ponownego logowania a nie do ponowienia`() {
        val text = AccountDeletionMessages.failure(halfway.copy(authRecentLoginRequired = true))

        assertTrue("brak wskazania ponownego logowania: $text", text.contains("zaloguj się ponownie"))
        assertFalse("ponowienie bez logowania nie pomoze: $text", text.contains("ponów próbę"))
    }

    @Test
    fun `brak odpowiedzi serwera przyznaje, ze nie wiadomo czy ostatni krok sie wykonal`() {
        val text = AccountDeletionMessages.failure(halfway.copy(writeTimedOut = true))

        assertTrue("brak przyznania niepewnosci: $text", text.contains("nie wiemy, czy ostatni krok się wykonał"))
        assertTrue("konto nadal ma byc opisane jako nieusuniete: $text", text.startsWith("Konto NIE zostało usunięte"))
    }

    @Test
    fun `brak odpowiedzi serwera przy zerowym postepie nie twierdzi, ze nic nie skasowano`() {
        val text = AccountDeletionMessages.failure(AccountDeletionResult.Failed(DeletionProgress(), writeTimedOut = true))

        assertFalse("nie wolno twierdzic, ze nic nie skasowano, gdy krok mogl przejsc: $text", text.contains("nic nie zostało skasowane"))
        assertTrue(text.contains("nie wiemy, czy ostatni krok się wykonał"))
    }

    @Test
    fun `komunikat o swiezym logowaniu mowi, ze nic nie usunieto`() {
        assertTrue(AccountDeletionMessages.RECENT_LOGIN_REQUIRED.contains("Nic nie zostało usunięte"))
        assertTrue(AccountDeletionMessages.RECENT_LOGIN_REQUIRED.contains("zaloguj ponownie"))
    }

    @Test
    fun `kod wymogu swiezego logowania ma polski komunikat zamiast zdania zastepczego`() {
        val error = object : FirebaseAuthException("ERROR_REQUIRES_RECENT_LOGIN", "This operation is sensitive") {}
        assertEquals(UserFacingError.RECENT_LOGIN_REQUIRED, UserFacingError.message(error, "fallback"))
    }

    @Test
    fun `ekran nie obiecuje trwalego usuniecia wszystkiego`() {
        val all = (listOf(AccountDeletionDisclosure.INTRO) +
            AccountDeletionDisclosure.removed(UserRole.PSYCHOLOGIST, 10) +
            AccountDeletionDisclosure.kept(UserRole.PSYCHOLOGIST).flatMap { listOf(it.what, it.why) })
            .joinToString(" ")
            .lowercase()

        assertFalse("tekst nie moze obiecywac trwalego usuniecia", all.contains("trwale"))
        assertFalse("tekst nie moze obiecywac usuniecia wszystkich danych", all.contains("wszystkie dane"))
    }

    @Test
    fun `ekran wymienia wizyty, opinie i ksiege portfela jako te, ktore zostaja`() {
        val kept = AccountDeletionDisclosure.kept(UserRole.PATIENT).joinToString(" ") { it.what }

        assertTrue("wizyty maja byc na liscie zostaje: $kept", kept.contains("Wizyty"))
        assertTrue("opinie maja byc na liscie zostaje: $kept", kept.contains("Opinie"))
        assertTrue("ksiega portfela ma byc na liscie zostaje: $kept", kept.contains("portfelu"))
    }

    @Test
    fun `psycholog widzi, ze opinie o jego pracy zostaja widoczne, a pacjent nie widzi tej pozycji`() {
        val psychologist = AccountDeletionDisclosure.kept(UserRole.PSYCHOLOGIST)
        val patient = AccountDeletionDisclosure.kept(UserRole.PATIENT).joinToString(" ") { it.what }
        val about = psychologist.firstOrNull { it.what.contains("wystawili Ci pacjenci") }

        assertTrue("psycholog ma zobaczyc, ze opinie o nim zostaja", about != null)
        assertTrue("pozycja ma tlumaczyc, ze oceny pozostana widoczne: ${about?.why}", about!!.why.contains("nadal będą widoczne"))
        assertFalse("pacjent nie ma opinii o sobie: $patient", patient.contains("wystawili Ci pacjenci"))
    }

    @Test
    fun `kazdy element z listy zostaje ma powod`() {
        AccountDeletionDisclosure.kept(UserRole.PSYCHOLOGIST).forEach {
            assertTrue("brak powodu przy: ${it.what}", it.why.isNotBlank())
        }
    }

    @Test
    fun `lista zniknie obejmuje profil, ogloszenia i konto, a notatki tylko dla psychologa`() {
        val patient = AccountDeletionDisclosure.removed(UserRole.PATIENT, 0).joinToString(" ")
        val psychologist = AccountDeletionDisclosure.removed(UserRole.PSYCHOLOGIST, 0).joinToString(" ")

        assertTrue(patient.contains("profil"))
        assertTrue(patient.contains("ogłoszenia"))
        assertTrue(patient.contains("Konto logowania"))
        assertFalse("pacjent nie prowadzi notatek terapeutycznych: $patient", patient.contains("Notatki terapeutyczne"))
        assertTrue("psycholog prowadzi notatki: $psychologist", psychologist.contains("Notatki terapeutyczne"))
    }

    @Test
    fun `saldo portfela przepada i ekran to mowi tylko gdy jest co tracic`() {
        val withBalance = AccountDeletionDisclosure.removed(UserRole.PATIENT, 3).joinToString(" ")
        val empty = AccountDeletionDisclosure.removed(UserRole.PATIENT, 0).joinToString(" ")

        assertTrue("brak informacji o utracie salda: $withBalance", withBalance.contains("3 monety przepadnie bez zwrotu"))
        assertFalse("saldo zero nie wymaga ostrzezenia: $empty", empty.contains("Saldo"))
    }

    @Test
    fun `pacjent widzi, ze notatki psychologow o nim zostaja`() {
        val patient = AccountDeletionDisclosure.kept(UserRole.PATIENT).joinToString(" ") { it.what }
        val psychologist = AccountDeletionDisclosure.kept(UserRole.PSYCHOLOGIST).joinToString(" ") { it.what }

        assertTrue(patient.contains("psychologowie prowadzili o Tobie"))
        assertFalse(psychologist.contains("psychologowie prowadzili o Tobie"))
    }

    @Test
    fun `porazka po terminach i zgloszeniu wymienia je wsrod usunietych i pozostalych`() {
        val text = AccountDeletionMessages.failure(
            AccountDeletionResult.Failed(
                DeletionProgress(
                    userDocumentDeleted = true,
                    slotsDeleted = 2,
                    slotsTotal = 2,
                    verificationPlanned = true
                )
            )
        )

        assertTrue("brak terminow wsrod usunietych: $text", text.contains("Już usunięto: profil, wolne terminy (2 z 2)"))
        assertTrue("brak zgloszenia wsrod pozostalych: $text", text.contains("Nadal istnieje: konto logowania, zgłoszenie weryfikacyjne"))
    }

    @Test
    fun `porazka w trakcie terminow podaje, ile wolnych terminow nadal istnieje`() {
        val text = AccountDeletionMessages.failure(
            AccountDeletionResult.Failed(DeletionProgress(slotsDeleted = 1, slotsTotal = 3))
        )

        assertTrue("brak licznika pozostalych terminow: $text", text.contains("Nadal istnieje: konto logowania, profil, 2 wolne terminy"))
    }

    @Test
    fun `usuniety wolny termin to postep, wiec komunikat nie twierdzi, ze nic nie skasowano`() {
        val text = AccountDeletionMessages.failure(AccountDeletionResult.Failed(DeletionProgress(slotsDeleted = 1, slotsTotal = 2)))

        assertFalse("skasowany termin to postep: $text", text.contains("nic nie zostało skasowane"))
    }

    @Test
    fun `ekran mowi, ze wolne terminy i zgloszenie ze zdjeciem znikaja, a wizyty zostaja`() {
        val removed = AccountDeletionDisclosure.removed(UserRole.PSYCHOLOGIST, 0).joinToString(" ")
        val kept = AccountDeletionDisclosure.kept(UserRole.PSYCHOLOGIST).joinToString(" ") { it.what }

        assertTrue("wolne terminy maja byc na liscie zniknie: $removed", removed.contains("wolne terminy"))
        assertTrue("zgloszenie ze zdjeciem ma byc na liscie zniknie: $removed", removed.contains("Zgłoszenie weryfikacyjne wraz ze zdjęciem dokumentu"))
        assertFalse("zgloszenie nie moze byc juz na liscie zostaje: $kept", kept.contains("Zgłoszenie weryfikacyjne"))
        assertFalse("wolne terminy nie moga byc juz na liscie zostaje: $kept", kept.contains("wolne terminy"))
        assertTrue("zarezerwowane i odbyte wizyty zostaja: $kept", kept.contains("Wizyty zarezerwowane"))
    }

    @Test
    fun `pacjent nie widzi terminow ani zgloszenia, ktorych nie ma`() {
        val removed = AccountDeletionDisclosure.removed(UserRole.PATIENT, 0).joinToString(" ")

        assertFalse("pacjent nie wystawia terminow: $removed", removed.contains("wolne terminy"))
        assertFalse("pacjent nie sklada zgloszen: $removed", removed.contains("Zgłoszenie weryfikacyjne"))
    }

    private fun bookedVisitsItem(role: UserRole) =
        AccountDeletionDisclosure.kept(role).first { it.what.startsWith("Wizyty zarezerwowane") }.what

    @Test
    fun `pacjent czyta, ze jego monety za wizyty przepadna, i nie czyta o zwrocie`() {
        val text = bookedVisitsItem(UserRole.PATIENT)
        assertTrue(text, text.contains("przepadną"))
        assertTrue(text, text.contains("nie odzyskasz ich już w żaden sposób"))
        assertFalse("nikt nie odzyska jest nieprawdą: psycholog może przez Zakończ", text.contains("nikt"))
        assertFalse(text, text.contains("wrócą"))
    }

    @Test
    fun `psycholog czyta, ze monety wroca do pacjentow po oknie zwrotu, i nie czyta, ze przepadly`() {
        listOf(UserRole.PSYCHOLOGIST, UserRole.PSYCHOLOGY_STUDENT).forEach { role ->
            val text = bookedVisitsItem(role)
            assertTrue(text, text.contains("wrócą do nich po upływie okna zwrotu"))
            assertFalse(text, text.contains("przepadną"))
            assertFalse(text, text.contains("nie wrócą"))
        }
    }
}
