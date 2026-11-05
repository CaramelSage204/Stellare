package com.example.stellare.ui.auth

sealed interface PhoneVerificationState {
    data class EnteringNumber(val error: String? = null) : PhoneVerificationState
    data object SendingCode : PhoneVerificationState
    data class EnteringCode(val phoneNumber: String, val error: String? = null) : PhoneVerificationState
    data class CheckingCode(val phoneNumber: String) : PhoneVerificationState
    data class Verified(val phoneNumber: String, val profileSaveFailed: Boolean = false) : PhoneVerificationState
}

object PhoneVerificationMessages {
    const val INVALID_NUMBER = "Podaj poprawny numer telefonu, np. +48 501 234 567."
    const val INVALID_CODE_FORMAT = "Kod składa się z 6 cyfr."
    const val SEND_FAILED = "Nie udało się wysłać kodu. Spróbuj ponownie."
    const val CONFIRM_FAILED = "Nie udało się potwierdzić numeru. Spróbuj ponownie."
    const val WRONG_CODE = "Nieprawidłowy kod. Sprawdź wiadomość SMS i spróbuj ponownie."
    const val CODE_EXPIRED = "Kod wygasł. Poproś o nowy."
    const val NUMBER_IN_USE = "Ten numer jest już powiązany z innym kontem."
    const val ALREADY_LINKED = "To konto ma już potwierdzony numer telefonu."
    const val QUOTA_EXCEEDED = "Wysłano zbyt wiele wiadomości SMS. Spróbuj ponownie później."
    const val SESSION_LOST = "Sesja wygasła. Zaloguj się ponownie."
}
