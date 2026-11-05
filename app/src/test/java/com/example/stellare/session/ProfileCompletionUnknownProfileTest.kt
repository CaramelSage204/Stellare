package com.example.stellare.session

import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.auth.AuthStatus
import com.example.stellare.ui.session.ProfileCompletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.IOException

/**
 * "Niezalogowany" i "nie wiem, bo odczyt padl" nie moga byc ta sama wartoscia: pierwsza odsyla na
 * logowanie albo na zakladanie profilu, druga nie ma prawa ruszyc zalogowanego uzytkownika.
 */
class ProfileCompletionUnknownProfileTest {

    private val dokument = UserModel(id = "u1", firstName = "Anna", role = UserRole.PATIENT)
    private val awaria = CurrentUserRead.Unavailable(IOException("odczyt profilu padl"))

    @Test
    fun `nieudany odczyt profilu nie jest brakiem profilu`() {
        assertNotEquals(
            "nieudany odczyt zmapowany na PROFILE_INCOMPLETE sadza zalogowanego uzytkownika na ekranie " +
                "zakladania profilu i zaprasza go do nadpisania wlasnego profilu nowym, pustym",
            AuthStatus.PROFILE_INCOMPLETE,
            ProfileCompletion.status("u1", awaria)
        )
    }

    @Test
    fun `nieudany odczyt profilu nie wylogowuje`() {
        assertNotEquals(
            "nieudany odczyt zmapowany na LOGGED_OUT wyrzuca zalogowanego uzytkownika na ekran " +
                "powitalny przy kazdej chwilowej awarii odczytu",
            AuthStatus.LOGGED_OUT,
            ProfileCompletion.status("u1", awaria)
        )
    }

    @Test
    fun `nieudany odczyt profilu zalogowanego daje stan nieznanego profilu`() {
        assertEquals(AuthStatus.PROFILE_UNAVAILABLE, ProfileCompletion.status("u1", awaria))
    }

    @Test
    fun `nieudany odczyt bez zalogowanego konta zostaje wylogowaniem`() {
        assertEquals(
            "bez identyfikatora z uwierzytelnienia nie ma kogo trzymac w aplikacji",
            AuthStatus.LOGGED_OUT,
            ProfileCompletion.status(null, awaria)
        )
    }

    @Test
    fun `potwierdzony brak dokumentu dalej prowadzi na zakladanie profilu`() {
        assertEquals(
            "nowe konto z logowania Google musi dalej trafiac na uzupelnienie profilu, inaczej " +
                "poprawka zamyka droge do zalozenia profilu w ogole",
            AuthStatus.PROFILE_INCOMPLETE,
            ProfileCompletion.status("u1", CurrentUserRead.Absent)
        )
    }

    @Test
    fun `wczytany dokument dalej wpuszcza do aplikacji`() {
        assertEquals(AuthStatus.LOGGED_IN, ProfileCompletion.status("u1", CurrentUserRead.Loaded(dokument)))
    }

    @Test
    fun `brak zalogowanego konta daje wylogowanie`() {
        assertEquals(AuthStatus.LOGGED_OUT, ProfileCompletion.status(null, CurrentUserRead.SignedOut))
    }

    @Test
    fun `zakladanie profilu wymagane jest wylacznie przy potwierdzonym braku dokumentu`() {
        val stany = listOf(CurrentUserRead.Loaded(dokument), CurrentUserRead.SignedOut, awaria)

        stany.forEach { read ->
            assertEquals(
                "tylko potwierdzony brak dokumentu wolno zamienic na zaproszenie do zalozenia profilu; " +
                    "przy $read zaproszenie konczy sie nadpisaniem prawdziwego profilu",
                false,
                ProfileCompletion.required("u1", read)
            )
        }
        assertEquals(true, ProfileCompletion.required("u1", CurrentUserRead.Absent))
    }
}
