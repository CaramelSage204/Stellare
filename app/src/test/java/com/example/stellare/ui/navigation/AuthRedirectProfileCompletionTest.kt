package com.example.stellare.ui.navigation

import com.example.stellare.ui.auth.AuthStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthRedirectProfileCompletionTest {

    private val appRoutes = listOf(
        Routes.FEED, Routes.CALENDAR, Routes.CHATS, Routes.WALLET,
        Routes.PROFILE_EDIT, Routes.MY_RATINGS, Routes.MY_CLIENTS, Routes.FAQ,
        Routes.POST_COMPOSER, Routes.VERIFICATION, Routes.VERIFICATION_ADMIN,
        Routes.PSYCHOLOGIST_DETAIL, Routes.CHAT_ROOM
    )

    @Test
    fun `uzytkownik bez profilu z kazdej trasy trafia na uzupelnienie profilu`() {
        (appRoutes + listOf(Routes.SPLASH, Routes.ONBOARDING, Routes.LOGIN, Routes.REGISTRATION)).forEach { route ->
            assertEquals(
                "bez profilu z $route uzytkownik ma trafic na uzupelnienie profilu, nie do ogloszen ani kalendarza",
                Routes.COMPLETE_PROFILE,
                AuthRedirect.target(AuthStatus.PROFILE_INCOMPLETE, route)
            )
        }
        assertEquals(Routes.COMPLETE_PROFILE, AuthRedirect.target(AuthStatus.PROFILE_INCOMPLETE, null))
    }

    @Test
    fun `uzytkownik bez profilu zostaje na ekranie uzupelnienia po odtworzeniu aktywnosci`() {
        assertNull(
            "ponowne przekierowanie na ekran, na ktorym juz jest, skasowaloby stos",
            AuthRedirect.target(AuthStatus.PROFILE_INCOMPLETE, Routes.COMPLETE_PROFILE)
        )
    }

    @Test
    fun `po zapisie profilu uzytkownik z ekranu uzupelnienia trafia do ogloszen`() {
        assertEquals(Routes.FEED, AuthRedirect.target(AuthStatus.LOGGED_IN, Routes.COMPLETE_PROFILE))
    }

    @Test
    fun `wylogowanie z ekranu uzupelnienia prowadzi na ekran powitalny`() {
        assertEquals(Routes.ONBOARDING, AuthRedirect.target(AuthStatus.LOGGED_OUT, Routes.COMPLETE_PROFILE))
    }

    @Test
    fun `status nieznany na ekranie uzupelnienia nigdzie nie przenosi`() {
        assertNull(AuthRedirect.target(AuthStatus.UNKNOWN, Routes.COMPLETE_PROFILE))
    }
}
