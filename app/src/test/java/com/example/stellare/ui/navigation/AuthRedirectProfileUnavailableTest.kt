package com.example.stellare.ui.navigation

import com.example.stellare.ui.auth.AuthStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class AuthRedirectProfileUnavailableTest {

    /**
     * Trasy wyliczane ze **źródła**, nie z ręcznej listy: ręczna rozjeżdża się po cichu przy
     * dodaniu kolejnej trasy, a wtedy zdanie "dla każdej trasy" przestaje być prawdziwe,
     * nie powodując padnięcia. Publiczne stałe tekstowe obiektu [Routes] to dokładnie trasy
     * (prywatne przedrostki i lista dolnego paska nie są publicznymi polami typu String).
     */
    private val allRoutes: List<String> = Routes::class.java.declaredFields
        .filter { it.type == String::class.java && Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) }
        .map { it.isAccessible = true; it.get(null) as String }
        .sorted()

    private val entryRoutes = listOf(Routes.SPLASH, Routes.ONBOARDING, Routes.LOGIN, Routes.REGISTRATION)

    private val appRoutes = allRoutes - entryRoutes.toSet() - Routes.COMPLETE_PROFILE

    @Test
    fun `wyliczenie tras obejmuje wszystkie trasy zadeklarowane w Routes`() {
        assertTrue(
            "test deklaruje wyczerpywalnosc, wiec musi widziec kazda trase; widzi: $allRoutes",
            allRoutes.containsAll(
                listOf(
                    Routes.SPLASH, Routes.ONBOARDING, Routes.LOGIN, Routes.REGISTRATION,
                    Routes.COMPLETE_PROFILE, Routes.FEED, Routes.CALENDAR, Routes.CHATS, Routes.WALLET,
                    Routes.PROFILE_EDIT, Routes.PHONE_VERIFICATION, Routes.MY_RATINGS, Routes.MY_CLIENTS,
                    Routes.FAQ, Routes.SETTINGS, Routes.DELETE_ACCOUNT, Routes.POST_COMPOSER,
                    Routes.VERIFICATION, Routes.VERIFICATION_ADMIN, Routes.PSYCHOLOGIST_DETAIL, Routes.CHAT_ROOM
                )
            )
        )
        assertEquals("Routes deklaruje 21 tras", 21, allRoutes.size)
    }

    @Test
    fun `nieznany profil nigdy nie prowadzi na zakladanie profilu`() {
        (allRoutes + listOf(null)).forEach { route ->
            assertNotEquals(
                "awaria odczytu profilu na trasie $route nie moze odeslac na zakladanie profilu: " +
                    "uzytkownik dostalby zaproszenie do nadpisania wlasnego profilu nowym, pustym",
                Routes.COMPLETE_PROFILE,
                AuthRedirect.target(AuthStatus.PROFILE_UNAVAILABLE, route)
            )
        }
    }

    @Test
    fun `nieznany profil nigdy nie prowadzi na ekran powitalny`() {
        (allRoutes + listOf(null)).forEach { route ->
            assertNotEquals(
                "awaria odczytu profilu na trasie $route nie moze wylogowac: zalogowanie jest pewne, " +
                    "nieznany jest sam profil",
                Routes.ONBOARDING,
                AuthRedirect.target(AuthStatus.PROFILE_UNAVAILABLE, route)
            )
        }
    }

    @Test
    fun `nieznany profil zostawia uzytkownika tam gdzie jest`() {
        appRoutes.forEach { route ->
            assertNull(
                "chwilowa awaria odczytu profilu nie jest powodem do ruszania uzytkownika z $route",
                AuthRedirect.target(AuthStatus.PROFILE_UNAVAILABLE, route)
            )
        }
    }

    @Test
    fun `nieznany profil nie kasuje formularza zakladania profilu`() {
        assertNull(
            "uzytkownik moze byc w polowie wypelniania formularza; przeniesienie go gdziekolwiek " +
                "kasuje wpisane pola",
            AuthRedirect.target(AuthStatus.PROFILE_UNAVAILABLE, Routes.COMPLETE_PROFILE)
        )
    }

    @Test
    fun `nieznany profil z ekranu startowego wchodzi do aplikacji`() {
        listOf(Routes.SPLASH, Routes.ONBOARDING, Routes.LOGIN, Routes.REGISTRATION).forEach { route ->
            assertEquals(
                "na $route nie da sie zostac: zalogowany uzytkownik czekalby bez konca na ekranie, " +
                    "ktory nie ma zadnego wyjscia",
                Routes.FEED,
                AuthRedirect.target(AuthStatus.PROFILE_UNAVAILABLE, route)
            )
        }
        assertEquals(Routes.FEED, AuthRedirect.target(AuthStatus.PROFILE_UNAVAILABLE, null))
    }
}
