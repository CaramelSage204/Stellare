package com.example.stellare.ui.navigation

import com.example.stellare.ui.auth.AuthStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthRedirectTest {

    private val appRoutes = listOf(
        Routes.FEED, Routes.CALENDAR, Routes.CHATS, Routes.WALLET,
        Routes.PROFILE_EDIT, Routes.MY_RATINGS, Routes.MY_CLIENTS, Routes.FAQ,
        Routes.POST_COMPOSER, Routes.VERIFICATION, Routes.VERIFICATION_ADMIN,
        Routes.PSYCHOLOGIST_DETAIL, Routes.CHAT_ROOM
    )

    @Test
    fun `zalogowany uzytkownik zostaje tam gdzie byl po odtworzeniu aktywnosci`() {
        appRoutes.forEach { route ->
            assertNull(
                "zalogowany na trasie $route nie może być odesłany na ekran startowy po zmianie konfiguracji",
                AuthRedirect.target(AuthStatus.LOGGED_IN, route)
            )
        }
    }

    @Test
    fun `zalogowany z ekranu wejsciowego trafia do ogloszen`() {
        listOf(Routes.SPLASH, Routes.ONBOARDING, Routes.LOGIN, Routes.REGISTRATION).forEach { route ->
            assertEquals(
                "po zalogowaniu z $route użytkownik ma trafić do ogłoszeń",
                Routes.FEED,
                AuthRedirect.target(AuthStatus.LOGGED_IN, route)
            )
        }
    }

    @Test
    fun `zalogowany bez ustalonej trasy trafia do ogloszen`() {
        assertEquals(Routes.FEED, AuthRedirect.target(AuthStatus.LOGGED_IN, null))
    }

    @Test
    fun `wylogowany zostaje na ekranach wejsciowych`() {
        listOf(Routes.ONBOARDING, Routes.LOGIN, Routes.REGISTRATION).forEach { route ->
            assertNull(
                "wylogowany na $route (np. w trakcie rejestracji) nie może być cofnięty do początku",
                AuthRedirect.target(AuthStatus.LOGGED_OUT, route)
            )
        }
    }

    @Test
    fun `wylogowany ze srodka aplikacji trafia na ekran powitalny`() {
        (appRoutes + Routes.SPLASH).forEach { route ->
            assertEquals(
                "wylogowanie na $route ma prowadzić na ekran powitalny",
                Routes.ONBOARDING,
                AuthRedirect.target(AuthStatus.LOGGED_OUT, route)
            )
        }
    }

    @Test
    fun `status nieznany nigdzie nie przenosi`() {
        (appRoutes + Routes.SPLASH).forEach { route ->
            assertNull("status UNKNOWN na $route nie może nawigować", AuthRedirect.target(AuthStatus.UNKNOWN, route))
        }
    }
}
