package com.example.stellare.ui.session

import com.example.stellare.data.model.UserRole

data class ProfileDraft(
    val role: UserRole,
    val firstName: String,
    val lastName: String,
    val ageText: String,
    val gender: String,
    val bio: String,
    val priceText: String
) {
    companion object {
        fun initial(suggestedName: Pair<String, String>, defaultGender: String) = ProfileDraft(
            role = UserRole.PATIENT,
            firstName = suggestedName.first,
            lastName = suggestedName.second,
            ageText = "",
            gender = defaultGender,
            bio = "",
            priceText = ""
        )
    }
}

object ProfileDiscard {
    fun needsConfirmation(initial: ProfileDraft, current: ProfileDraft): Boolean = current != initial
}
