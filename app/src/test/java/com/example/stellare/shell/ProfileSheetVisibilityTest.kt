package com.example.stellare.shell

import com.example.stellare.ui.auth.AuthStatus
import com.example.stellare.ui.shell.ProfileSheetVisibility
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSheetVisibilityTest {

    @Test
    fun `panel profilu nie przezywa przejscia na uzupelnianie profilu`() {
        assertFalse(
            "otwarty panel z napisem Uzytkownik i znakiem zapytania musi sie zamknac, gdy dokument znika i aplikacja przechodzi na uzupelnianie profilu",
            ProfileSheetVisibility.mayStayOpen(AuthStatus.PROFILE_INCOMPLETE)
        )
    }

    @Test
    fun `panel profilu moze byc otwarty dla zalogowanego`() {
        assertTrue(
            "zalogowany z dokumentem ma moc otworzyc panel profilu",
            ProfileSheetVisibility.mayStayOpen(AuthStatus.LOGGED_IN)
        )
    }

    @Test
    fun `panel profilu nie zamyka sie przy stanie nieznanym`() {
        assertTrue(
            "stan nieznany to chwilowy brak odpowiedzi zrodla, nie powod do zamykania panelu",
            ProfileSheetVisibility.mayStayOpen(AuthStatus.UNKNOWN)
        )
    }

    @Test
    fun `panel profilu nie zamyka sie przy nieudanym odczycie profilu`() {
        assertTrue(
            "awaria odczytu profilu nie moze zatrzasnac panelu pod palcem: ostatni znany profil " +
                "zostaje widoczny, a o awarii mowi komunikat. Zamkniecie panelu byloby tym samym " +
                "bledem co wyrzucenie uzytkownika z ekranu",
            ProfileSheetVisibility.mayStayOpen(AuthStatus.PROFILE_UNAVAILABLE)
        )
    }

    @Test
    fun `panel profilu nie jest zamykany decyzja przy wylogowaniu`() {
        assertTrue(
            "wylogowanie z panelu zamyka go wlasna animacja, ta decyzja ma dotyczyc tylko uzupelniania profilu",
            ProfileSheetVisibility.mayStayOpen(AuthStatus.LOGGED_OUT)
        )
    }
}
