package com.example.stellare.ui.shell

import com.example.stellare.data.model.UserRole

object ProfileSheetPresentation {

    fun avatarInitial(firstName: String?): String =
        firstName?.firstOrNull()?.toString()?.uppercase() ?: "?"

    fun displayName(firstName: String?, lastName: String?): String =
        "${firstName.orEmpty()} ${lastName.orEmpty()}".trim().ifEmpty { "Użytkownik" }

    fun roleLabel(role: UserRole?): String = when (role) {
        UserRole.PSYCHOLOGIST -> "Psycholog"
        UserRole.PSYCHOLOGY_STUDENT -> "Student Psychologii"
        else -> "Pacjent"
    }

    fun favoritesHeading(isPsycholog: Boolean): String =
        if (isPsycholog) "Ulubieni pacjenci" else "Ulubieni psycholodzy"

    fun favoriteSubtitle(isPsycholog: Boolean, age: Int, specializations: String): String =
        if (isPsycholog) {
            "Pacjent • $age lat"
        } else {
            specializations.split(",").firstOrNull()?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "Psycholog"
        }
}
