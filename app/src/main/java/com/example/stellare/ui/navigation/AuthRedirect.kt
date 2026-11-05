package com.example.stellare.ui.navigation

import com.example.stellare.ui.auth.AuthStatus

object AuthRedirect {
    private val SignedOutRoutes = setOf(Routes.ONBOARDING, Routes.LOGIN, Routes.REGISTRATION)
    private val EntryRoutes = SignedOutRoutes + Routes.SPLASH + Routes.COMPLETE_PROFILE

    /**
     * [AuthStatus.PROFILE_UNAVAILABLE] wychodzi tylko z ekranów, na których nie da się zostać
     * (powitanie, logowanie, rejestracja, ekran startowy), i celuje w aplikację, bo o zalogowaniu
     * wiadomo na pewno — nieznany jest sam profil. Nie ma na tej liście [Routes.COMPLETE_PROFILE]:
     * awaria odczytu w trakcie wypełniania formularza nie może skasować wpisanych pól. Z każdego
     * innego ekranu nie przenosi nigdzie, bo chwilowa awaria odczytu nie jest powodem do ruszania
     * użytkownika z miejsca.
     */
    private val UnknownProfileExits = SignedOutRoutes + Routes.SPLASH

    fun target(status: AuthStatus, currentRoute: String?): String? = when (status) {
        AuthStatus.LOGGED_IN ->
            if (currentRoute == null || currentRoute in EntryRoutes) Routes.FEED else null
        AuthStatus.PROFILE_INCOMPLETE ->
            if (currentRoute == Routes.COMPLETE_PROFILE) null else Routes.COMPLETE_PROFILE
        AuthStatus.PROFILE_UNAVAILABLE ->
            if (currentRoute == null || currentRoute in UnknownProfileExits) Routes.FEED else null
        AuthStatus.LOGGED_OUT ->
            if (currentRoute != null && currentRoute in SignedOutRoutes) null else Routes.ONBOARDING
        AuthStatus.UNKNOWN -> null
    }
}
