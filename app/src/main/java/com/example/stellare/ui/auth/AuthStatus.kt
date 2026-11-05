package com.example.stellare.ui.auth

/**
 * [PROFILE_INCOMPLETE] mówi "serwer potwierdza, że profilu nie ma" i wolno mu wysłać użytkownika
 * na zakładanie profilu. [PROFILE_UNAVAILABLE] mówi "nie wiem, bo odczyt padł" — i nie wolno mu
 * ruszyć zalogowanego użytkownika z miejsca, w którym jest, ani zaprosić go do nadpisania
 * własnego profilu. [UNKNOWN] to brak jakiejkolwiek odpowiedzi od źródła tożsamości.
 */
enum class AuthStatus {
    UNKNOWN,
    LOGGED_IN,
    PROFILE_INCOMPLETE,
    PROFILE_UNAVAILABLE,
    LOGGED_OUT
}
