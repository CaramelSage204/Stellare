package com.example.stellare.ui.feed

data class FeedFilters(
    val query: String = "",
    val ageMin: Int = 18,
    val ageMax: Int = 100,
    val gender: String = WSZYSTKIE,
    val specialization: String = WSZYSTKIE,
    val verifiedOnly: Boolean = false,
    val priceMax: Double = 400.0,
    val minRating: Double = 0.0
) {
    fun matches(user: com.example.stellare.data.model.UserModel): Boolean {
        val pasujeTekst = query.isEmpty() ||
            "${user.firstName} ${user.lastName}".contains(query, ignoreCase = true) ||
            user.specializations.contains(query, ignoreCase = true)
        val pasujeWiek = user.age in ageMin..ageMax
        val pasujePlec = gender == WSZYSTKIE || user.gender.equals(gender, ignoreCase = true)
        val pasujeSpec = specialization == WSZYSTKIE ||
            user.specializations.contains(specialization, ignoreCase = true)
        val pasujeWeryfikacja = !verifiedOnly || user.isVerified
        val pasujeCena = user.pricePerSession <= priceMax
        val pasujeOcena = user.rating >= minRating
        return pasujeTekst && pasujeWiek && pasujePlec && pasujeSpec &&
            pasujeWeryfikacja && pasujeCena && pasujeOcena
    }

    companion object {
        const val WSZYSTKIE = "Wszystkie"
    }
}
