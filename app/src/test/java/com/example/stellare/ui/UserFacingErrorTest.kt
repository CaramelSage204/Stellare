package com.example.stellare.ui

import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.stellare.data.model.UserMessageException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingErrorTest {

    private val fallback = "Nie udało się."

    @Test
    fun `odrzucenie przez regule nie pokazuje numeru linii`() {
        val blad = FirebaseFirestoreException(
            "PERMISSION_DENIED: evaluation error at L234:12 for create",
            FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
        assertEquals(fallback, UserFacingError.message(blad, fallback))
    }

    @Test
    fun `kontencja transakcji nie udaje bledu regul i prosi o ponowienie`() {
        val blad = FirebaseFirestoreException("ABORTED: too much contention", FirebaseFirestoreException.Code.ABORTED)
        assertEquals(UserFacingError.TRY_AGAIN, UserFacingError.message(blad, fallback))
    }

    @Test
    fun `kontencja jest opisana ogolnie i nie wspomina o ocenianiu`() {
        val blad = FirebaseFirestoreException("ABORTED: too much contention", FirebaseFirestoreException.Code.ABORTED)
        val text = UserFacingError.message(blad, fallback)
        assertFalse(
            "interfejs opisuje operację (ocenianie), której użytkownik nie wykonywał: $text",
            text.contains("ocen", ignoreCase = true)
        )
        assertTrue("komunikat musi prosić o ponowienie: $text", text.contains("Spróbuj jeszcze raz"))
    }

    @Test
    fun `kontencja przy wystawianiu oceny mowi o ocenianiu`() {
        val blad = FirebaseFirestoreException("ABORTED: too much contention", FirebaseFirestoreException.Code.ABORTED)
        assertEquals(
            UserFacingError.RATING_TRY_AGAIN,
            UserFacingError.message(blad, fallback, contention = UserFacingError.RATING_TRY_AGAIN)
        )
    }

    @Test
    fun `brak sieci daje komunikat o polaczeniu`() {
        val blad = FirebaseFirestoreException("Unable to resolve host", FirebaseFirestoreException.Code.UNAVAILABLE)
        assertEquals(UserFacingError.NO_CONNECTION, UserFacingError.message(blad, fallback))
    }

    @Test
    fun `blad Firestore zawiniety w inny wyjatek tez jest tlumaczony`() {
        val wewnetrzny = FirebaseFirestoreException("PERMISSION_DENIED L9", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        assertEquals(fallback, UserFacingError.message(RuntimeException("opakowanie", wewnetrzny), fallback))
    }

    @Test
    fun `techniczny tekst bez klasy Firestore tez jest ukrywany`() {
        assertEquals(fallback, UserFacingError.message(IllegalStateException("PERMISSION_DENIED: x"), fallback))
    }

    @Test
    fun `komunikat domenowy przechodzi bez zmian`() {
        val domenowy = UserMessageException("Termin został już zarezerwowany.")
        assertEquals("Termin został już zarezerwowany.", UserFacingError.message(domenowy, fallback))
    }

    @Test
    fun `brak tresci daje fallback`() {
        assertEquals(fallback, UserFacingError.message(RuntimeException(), fallback))
    }

    private fun assertPolish(text: String) {
        assertFalse(text, text.contains("badly formatted"))
        assertFalse(text, text.contains("ERROR_"))
        assertFalse(text, text.contains("There is no user"))
    }

    @Test
    fun `zly format adresu daje polskie zdanie`() {
        val blad = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "The email address is badly formatted.")
        val tekst = UserFacingError.message(blad, fallback)
        assertEquals("Adres e-mail ma nieprawidłowy format.", tekst)
        assertPolish(tekst)
    }

    @Test
    fun `zle haslo daje polskie zdanie`() {
        val blad = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "The password is invalid or the user does not have a password.")
        assertEquals("Błędny e-mail lub hasło.", UserFacingError.message(blad, fallback))
    }

    @Test
    fun `nowy kod nieprawidlowych danych daje to samo zdanie co zle haslo`() {
        val blad = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "The supplied auth credential is incorrect, malformed or has expired.")
        assertEquals("Błędny e-mail lub hasło.", UserFacingError.message(blad, fallback))
    }

    @Test
    fun `konto nie istnieje daje to samo zdanie co zle haslo`() {
        val blad = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "There is no user record corresponding to this identifier.")
        val tekst = UserFacingError.message(blad, fallback)
        assertEquals("Błędny e-mail lub hasło.", tekst)
        assertPolish(tekst)
    }

    @Test
    fun `komunikaty konta nieistniejacego i blednego hasla sa identyczne`() {
        val brakKonta = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "There is no user record corresponding to this identifier.")
        val zleHaslo = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "The password is invalid or the user does not have a password.")
        assertEquals(UserFacingError.message(zleHaslo, fallback), UserFacingError.message(brakKonta, fallback))
    }

    @Test
    fun `konto juz istnieje`() {
        val blad = FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "The email address is already in use by another account.")
        assertEquals("Konto o tym adresie e-mail już istnieje.", UserFacingError.message(blad, fallback))
    }

    @Test
    fun `zbyt slabe haslo`() {
        val blad = FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "Password should be at least 6 characters", "Password should be at least 6 characters")
        assertEquals("Hasło jest zbyt słabe. Użyj co najmniej 6 znaków.", UserFacingError.message(blad, fallback))
    }

    @Test
    fun `brak sieci w autoryzacji`() {
        val blad = FirebaseNetworkException("A network error (such as timeout, interrupted connection or unreachable host) has occurred.")
        assertEquals(UserFacingError.NO_CONNECTION, UserFacingError.message(blad, fallback))
    }

    @Test
    fun `zbyt wiele prob`() {
        val blad = FirebaseTooManyRequestsException("We have blocked all requests from this device due to unusual activity.")
        assertEquals(UserFacingError.TOO_MANY_ATTEMPTS, UserFacingError.message(blad, fallback))
    }

    @Test
    fun `nieznany kod autoryzacji daje fallback a nie tekst Firebase`() {
        val blad = FirebaseAuthException("ERROR_SOMETHING_NEW", "An internal Firebase problem occurred in English.")
        assertEquals(fallback, UserFacingError.message(blad, fallback))
    }

    @Test
    fun `kod autoryzacji bez wartosci daje fallback`() {
        val blad = FirebaseAuthException("", "Raw English text")
        assertEquals(fallback, UserFacingError.message(blad, fallback))
    }

    @Test
    fun `blad autoryzacji zawiniety w inny wyjatek tez jest tlumaczony`() {
        val wewnetrzny = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "The email address is badly formatted.")
        assertEquals("Adres e-mail ma nieprawidłowy format.", UserFacingError.message(RuntimeException("opakowanie", wewnetrzny), fallback))
    }

    @Test
    fun `wyjatek spoza Firebase w logowaniu nie pokazuje angielskiego tekstu`() {
        val blad = IllegalStateException("Unexpected failure while reading credential")
        assertEquals(fallback, UserFacingError.authMessage(blad, fallback))
    }

    @Test
    fun `anulowanie wyboru konta Google daje polskie zdanie`() {
        val blad = GetCredentialCancellationException("activity is cancelled by the user.")
        assertEquals(UserFacingError.GOOGLE_SIGN_IN_CANCELLED, UserFacingError.authMessage(blad, fallback))
    }

    @Test
    fun `brak konta Google na urzadzeniu daje polskie zdanie`() {
        val blad = NoCredentialException("No credentials available")
        assertEquals(UserFacingError.NO_GOOGLE_ACCOUNT, UserFacingError.authMessage(blad, fallback))
    }

    @Test
    fun `inny wyjatek Credential Managera daje fallback a nie angielski tekst`() {
        val blad = GetCredentialUnknownException("An unknown error occurred during get credential")
        assertEquals(fallback, UserFacingError.authMessage(blad, fallback))
    }

    @Test
    fun `wyjatek Credential Managera zawiniety w inny wyjatek tez jest tlumaczony`() {
        val blad = RuntimeException("opakowanie", GetCredentialCancellationException("cancelled"))
        assertEquals(UserFacingError.GOOGLE_SIGN_IN_CANCELLED, UserFacingError.authMessage(blad, fallback))
    }

    @Test
    fun `authMessage zachowuje tlumaczenie wyjatkow Firebase`() {
        val blad = FirebaseNetworkException("A network error has occurred.")
        assertEquals(UserFacingError.NO_CONNECTION, UserFacingError.authMessage(blad, fallback))
    }

    @Test
    fun `mapowanie kodow nie zna kodu null`() {
        assertEquals(null, UserFacingError.authCodeMessage(null))
        assertEquals(null, UserFacingError.authCodeMessage("ERROR_UNKNOWN"))
    }
}
