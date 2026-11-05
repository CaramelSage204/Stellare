package com.example.stellare.ui.session

import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.auth.PhoneNumber

object RegistrationRules {

    const val PRICE_REQUIRED = "Podaj cenę sesji."
    const val PRICE_INVALID = "Cena sesji musi być liczbą całkowitą większą od zera."

    const val FIRST_NAME_REQUIRED = "Podaj imię."
    const val LAST_NAME_REQUIRED = "Podaj nazwisko."
    const val AGE_INVALID = "Wiek musi być liczbą całkowitą większą od zera."

    const val PHONE_INVALID = "Podaj poprawny numer telefonu, np. +48 501 234 567, albo zostaw pole puste."
    const val ROLE_STEP = 1
    const val IDENTITY_STEP = 2
    const val PHONE_STEP = 3
    const val PRICE_STEP = 5

    fun totalSteps(role: UserRole): Int = if (isProfessional(role)) 5 else 4

    fun stepLabel(step: Int, role: UserRole): String =
        if (step == ROLE_STEP) "Krok $ROLE_STEP: wybierz rolę" else "Krok $step z ${totalSteps(role)}"

    fun progress(step: Int, role: UserRole): Float =
        if (step == ROLE_STEP) 0f else step.toFloat() / totalSteps(role)

    fun stepBlockMessage(
        step: Int,
        role: UserRole,
        firstName: String,
        lastName: String,
        ageText: String,
        phone: String,
        priceText: String
    ): String? = when (step) {
        IDENTITY_STEP -> identityError(firstName, lastName, ageText)
        PHONE_STEP -> if (phone.isNotBlank() && PhoneNumber.normalize(phone) == null) PHONE_INVALID else null
        PRICE_STEP -> if (isProfessional(role)) priceError(priceText) else null
        else -> null
    }

    fun isProfessional(role: UserRole): Boolean =
        role == UserRole.PSYCHOLOGIST || role == UserRole.PSYCHOLOGY_STUDENT

    fun parsePrice(text: String): Double? {
        val value = text.trim().toIntOrNull() ?: return null
        return if (value > 0) value.toDouble() else null
    }

    fun parseAge(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it > 0 }

    fun identityError(firstName: String, lastName: String, ageText: String): String? = when {
        firstName.isBlank() -> FIRST_NAME_REQUIRED
        lastName.isBlank() -> LAST_NAME_REQUIRED
        parseAge(ageText) == null -> AGE_INVALID
        else -> null
    }

    fun priceError(text: String): String? = when {
        text.isBlank() -> PRICE_REQUIRED
        parsePrice(text) == null -> PRICE_INVALID
        else -> null
    }
}
