package com.example.stellare.ui.session

import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.auth.AuthStatus

object ProfileCompletion {

    fun required(signedInUid: String?, read: CurrentUserRead): Boolean =
        signedInUid != null && read == CurrentUserRead.Absent

    /**
     * Zakładanie profilu wolno zaproponować wyłącznie przy [CurrentUserRead.Absent], czyli gdy
     * serwer potwierdził, że dokumentu nie ma. [CurrentUserRead.Unavailable] daje
     * [AuthStatus.PROFILE_UNAVAILABLE], bo nieudany odczyt nie jest dowodem braku profilu —
     * a z zaproszenia do założenia nowego powstałby zapis nadpisujący prawdziwy profil.
     */
    fun status(signedInUid: String?, read: CurrentUserRead): AuthStatus = when (read) {
        is CurrentUserRead.Loaded ->
            if (signedInUid == null) AuthStatus.LOGGED_OUT else AuthStatus.LOGGED_IN
        CurrentUserRead.Absent ->
            if (required(signedInUid, read)) AuthStatus.PROFILE_INCOMPLETE else AuthStatus.LOGGED_OUT
        is CurrentUserRead.Unavailable ->
            if (signedInUid == null) AuthStatus.LOGGED_OUT else AuthStatus.PROFILE_UNAVAILABLE
        CurrentUserRead.SignedOut -> AuthStatus.LOGGED_OUT
    }

    fun splitDisplayName(displayName: String?): Pair<String, String> {
        val trimmed = displayName?.trim().orEmpty()
        val first = trimmed.substringBefore(' ')
        return first to trimmed.substring(first.length).trim()
    }

    fun blockMessage(
        role: UserRole,
        firstName: String,
        lastName: String,
        ageText: String,
        priceText: String
    ): String? =
        RegistrationRules.identityError(firstName, lastName, ageText)
            ?: if (RegistrationRules.isProfessional(role)) RegistrationRules.priceError(priceText) else null

    fun priceFor(role: UserRole, priceText: String): Double =
        if (RegistrationRules.isProfessional(role)) RegistrationRules.parsePrice(priceText) ?: 0.0 else 0.0
}
