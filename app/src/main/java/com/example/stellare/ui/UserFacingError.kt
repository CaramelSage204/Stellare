package com.example.stellare.ui

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.stellare.data.model.UserMessageException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.FirebaseException
import com.google.firebase.firestore.FirebaseFirestoreException

object UserFacingError {

    const val TRY_AGAIN = "Dane zmieniły się w trakcie operacji. Spróbuj jeszcze raz."
    const val RATING_TRY_AGAIN = "Wiele osób oceniało w tym samym czasie. Spróbuj jeszcze raz."
    const val NO_CONNECTION = "Brak połączenia z serwerem. Sprawdź internet i spróbuj ponownie."
    const val TOO_MANY_ATTEMPTS = "Zbyt wiele prób. Odczekaj chwilę i spróbuj ponownie."
    const val PROFILE_READ_FAILED = "Nie udało się odczytać Twojego profilu. Spróbuj ponownie."
    const val GOOGLE_SIGN_IN_CANCELLED = "Logowanie przez Google zostało anulowane."
    const val NO_GOOGLE_ACCOUNT = "Na tym urządzeniu nie znaleziono konta Google."
    const val FACEBOOK_SIGN_IN_CANCELLED = "Logowanie przez Facebooka zostało anulowane."
    const val SIGN_IN_METHOD_UNAVAILABLE = "Ten sposób logowania jest obecnie niedostępny."
    const val ACCOUNT_EXISTS_WITH_OTHER_METHOD =
        "Konto z tym adresem e-mail istnieje już z innym sposobem logowania. Zaloguj się tak, jak przy zakładaniu konta."

    const val RECENT_LOGIN_REQUIRED =
        "Ze względów bezpieczeństwa ta operacja wymaga świeżego logowania. Wyloguj się, zaloguj ponownie i spróbuj jeszcze raz."

    private val technicalMarkers = Regex(
        "PERMISSION_DENIED|FAILED_PRECONDITION|DEADLINE_EXCEEDED|UNAVAILABLE|UNAUTHENTICATED|ABORTED|" +
            """evaluation error|insufficient permissions|FirebaseFirestore|com\.google|io\.grpc|java\.|kotlin\.|\bL\d+:\d+"""
    )

    private val authMessages = mapOf(
        "ERROR_INVALID_EMAIL" to "Adres e-mail ma nieprawidłowy format.",
        "ERROR_WRONG_PASSWORD" to "Błędny e-mail lub hasło.",
        "ERROR_INVALID_CREDENTIAL" to "Błędny e-mail lub hasło.",
        "ERROR_USER_NOT_FOUND" to "Błędny e-mail lub hasło.",
        "ERROR_EMAIL_ALREADY_IN_USE" to "Konto o tym adresie e-mail już istnieje.",
        "ERROR_WEAK_PASSWORD" to "Hasło jest zbyt słabe. Użyj co najmniej 6 znaków.",
        "ERROR_NETWORK_REQUEST_FAILED" to NO_CONNECTION,
        "ERROR_TOO_MANY_REQUESTS" to TOO_MANY_ATTEMPTS,
        "ERROR_USER_DISABLED" to "To konto zostało zablokowane.",
        "ERROR_WEB_CONTEXT_CANCELED" to FACEBOOK_SIGN_IN_CANCELLED,
        "ERROR_OPERATION_NOT_ALLOWED" to SIGN_IN_METHOD_UNAVAILABLE,
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" to ACCOUNT_EXISTS_WITH_OTHER_METHOD,
        "ERROR_REQUIRES_RECENT_LOGIN" to RECENT_LOGIN_REQUIRED
    )

    fun authCodeMessage(code: String?): String? = code?.let { authMessages[it] }

    fun authMessage(error: Throwable, fallback: String): String {
        val chain = generateSequence(error) { it.cause }.toList()
        if (chain.any { it is GetCredentialCancellationException }) return GOOGLE_SIGN_IN_CANCELLED
        if (chain.any { it is NoCredentialException }) return NO_GOOGLE_ACCOUNT
        if (chain.any { it is FirebaseException }) return message(error, fallback)
        return fallback
    }

    fun message(error: Throwable, fallback: String, contention: String = TRY_AGAIN): String {
        val chain = generateSequence(error) { it.cause }.toList()
        val firestore = chain.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestore != null) {
            return when (firestore.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> NO_CONNECTION
                FirebaseFirestoreException.Code.ABORTED -> contention
                else -> fallback
            }
        }
        if (chain.any { it is FirebaseNetworkException }) return NO_CONNECTION
        if (chain.any { it is FirebaseTooManyRequestsException }) return TOO_MANY_ATTEMPTS
        val auth = chain.filterIsInstance<FirebaseAuthException>().firstOrNull()
        if (auth != null) return authCodeMessage(auth.errorCode) ?: fallback
        if (chain.any { it is FirebaseException }) return fallback
        val own = chain.filterIsInstance<UserMessageException>().firstOrNull()
        val text = own?.message ?: return fallback
        return if (text.isBlank() || technicalMarkers.containsMatchIn(text)) fallback else text
    }
}
