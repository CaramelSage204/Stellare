package com.example.stellare.ui.auth

sealed interface PasswordResetState {
    data object Idle : PasswordResetState
    data object Sending : PasswordResetState
    data class Sent(val message: String) : PasswordResetState
    data class Failed(val message: String) : PasswordResetState
}

object PasswordResetMessages {
    const val SENT = "Jeśli konto o tym adresie istnieje, wysłaliśmy na niego link do zmiany hasła. Sprawdź skrzynkę, także folder ze spamem."
    const val EMPTY_EMAIL = "Podaj adres e-mail, na który mamy wysłać link."
    const val FAILED = "Nie udało się wysłać linku. Spróbuj ponownie."
}