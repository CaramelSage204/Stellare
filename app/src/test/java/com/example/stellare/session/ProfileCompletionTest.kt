package com.example.stellare.session

import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.auth.AuthStatus
import com.example.stellare.ui.session.ProfileCompletion
import com.example.stellare.ui.session.RegistrationRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCompletionTest {

    private val dokument = UserModel(id = "u1", firstName = "Anna", role = UserRole.PATIENT)

    @Test
    fun `brak identyfikatora nie wymaga uzupelnienia bo uzytkownik jest wylogowany`() {
        assertFalse(
            "bez identyfikatora z uwierzytelnienia nie ma czego uzupelniac, to wylogowany",
            ProfileCompletion.required(signedInUid = null, read = CurrentUserRead.Absent)
        )
        assertFalse(
            "dokument bez identyfikatora tez nie moze wymagac uzupelnienia",
            ProfileCompletion.required(signedInUid = null, read = CurrentUserRead.Loaded(dokument))
        )
    }

    @Test
    fun `identyfikator bez dokumentu wymaga uzupelnienia`() {
        assertTrue(
            "zalogowany bez dokumentu (pierwsze logowanie przez Google) musi zostac skierowany na uzupelnienie profilu",
            ProfileCompletion.required(signedInUid = "u1", read = CurrentUserRead.Absent)
        )
    }

    @Test
    fun `identyfikator z dokumentem nie wymaga uzupelnienia`() {
        assertFalse(
            "zalogowany z dokumentem ma wejsc do aplikacji",
            ProfileCompletion.required(signedInUid = "u1", read = CurrentUserRead.Loaded(dokument))
        )
    }

    @Test
    fun `status wylogowany bez identyfikatora`() {
        assertEquals(AuthStatus.LOGGED_OUT, ProfileCompletion.status(null, CurrentUserRead.SignedOut))
    }

    @Test
    fun `status uzupelnianie profilu dla identyfikatora bez dokumentu`() {
        assertEquals(AuthStatus.PROFILE_INCOMPLETE, ProfileCompletion.status("u1", CurrentUserRead.Absent))
    }

    @Test
    fun `status zalogowany dla identyfikatora z dokumentem`() {
        assertEquals(AuthStatus.LOGGED_IN, ProfileCompletion.status("u1", CurrentUserRead.Loaded(dokument)))
    }

    @Test
    fun `komplet danych pacjenta nie jest blokowany`() {
        assertNull(ProfileCompletion.blockMessage(UserRole.PATIENT, "Jan", "Kowalski", "30", ""))
    }

    @Test
    fun `puste imie i nazwisko blokuja zapis`() {
        assertEquals(RegistrationRules.FIRST_NAME_REQUIRED, ProfileCompletion.blockMessage(UserRole.PATIENT, " ", "Kowalski", "30", ""))
        assertEquals(RegistrationRules.LAST_NAME_REQUIRED, ProfileCompletion.blockMessage(UserRole.PATIENT, "Jan", "", "30", ""))
    }

    @Test
    fun `wiek musi byc dodatnia liczba calkowita`() {
        listOf("", "abc", "0", "-3", "2.5").forEach { wiek ->
            assertEquals(
                "wiek '$wiek' ma zablokowac zapis komunikatem o wieku",
                RegistrationRules.AGE_INVALID,
                ProfileCompletion.blockMessage(UserRole.PATIENT, "Jan", "Kowalski", wiek, "")
            )
        }
    }

    @Test
    fun `psycholog bez dodatniej ceny jest blokowany komunikatem z RegistrationRules`() {
        listOf(UserRole.PSYCHOLOGIST, UserRole.PSYCHOLOGY_STUDENT).forEach { rola ->
            assertEquals(RegistrationRules.PRICE_REQUIRED, ProfileCompletion.blockMessage(rola, "Jan", "Kowalski", "30", ""))
            assertEquals(RegistrationRules.PRICE_INVALID, ProfileCompletion.blockMessage(rola, "Jan", "Kowalski", "30", "0"))
            assertEquals(RegistrationRules.PRICE_INVALID, ProfileCompletion.blockMessage(rola, "Jan", "Kowalski", "30", "12,5"))
            assertNull(ProfileCompletion.blockMessage(rola, "Jan", "Kowalski", "30", "150"))
        }
    }

    @Test
    fun `pacjent nie potrzebuje ceny`() {
        assertNull(
            "cena nie dotyczy pacjenta, nawet bledna nie moze go blokowac",
            ProfileCompletion.blockMessage(UserRole.PATIENT, "Jan", "Kowalski", "30", "abc")
        )
    }

    @Test
    fun `cena zapisywana tylko dla roli zawodowej`() {
        assertEquals(150.0, ProfileCompletion.priceFor(UserRole.PSYCHOLOGIST, "150"), 0.0)
        assertEquals(0.0, ProfileCompletion.priceFor(UserRole.PATIENT, "150"), 0.0)
    }

    @Test
    fun `nazwa z konta Google dzieli sie na imie i nazwisko`() {
        assertEquals("Ewa" to "Nowak", ProfileCompletion.splitDisplayName("Ewa Nowak"))
        assertEquals("Anna" to "Maria Kowalska", ProfileCompletion.splitDisplayName("  Anna Maria Kowalska "))
    }

    @Test
    fun `brak nazwy z konta Google zostawia pola puste`() {
        assertEquals("" to "", ProfileCompletion.splitDisplayName(null))
        assertEquals("" to "", ProfileCompletion.splitDisplayName("   "))
        assertEquals("Cher" to "", ProfileCompletion.splitDisplayName("Cher"))
    }
}
