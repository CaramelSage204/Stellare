package com.example.stellare.ui.profile

import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.psychologistdetail.ReviewSummary
import com.example.stellare.ui.psychologistdetail.ReviewsLoad
import com.example.stellare.ui.session.RegistrationRules

enum class ServiceKind { Audio, Video }

fun serviceKind(serviceName: String): ServiceKind =
    if (serviceName.contains("wideo", ignoreCase = true)) ServiceKind.Video else ServiceKind.Audio

fun newServicePrice(text: String): Int? = RegistrationRules.parsePrice(text)?.toInt()

fun newServiceError(text: String): String? = RegistrationRules.priceError(text)

fun sessionPriceText(price: Double): String =
    if (price == Math.floor(price) && !price.isInfinite()) price.toLong().toString() else price.toString()

fun priceError(specialist: Boolean, text: String): String? =
    if (specialist) RegistrationRules.priceError(text) else null

fun priceToSave(specialist: Boolean, text: String, current: Double): Double? =
    if (specialist) RegistrationRules.parsePrice(text) else current

fun shouldHydrateProfileForm(hydratedUserId: String?, loadedUserId: String): Boolean =
    hydratedUserId != loadedUserId

fun canSave(userLoaded: Boolean, priceError: String?): Boolean =
    userLoaded && priceError == null

fun isSpecialist(role: UserRole?): Boolean =
    role == UserRole.PSYCHOLOGIST || role == UserRole.PSYCHOLOGY_STUDENT

fun profileReviewSummary(specialist: Boolean, load: ReviewsLoad?): ReviewSummary? =
    if (specialist) ReviewSummary.fromLoad(load) else null

fun profileSubtitle(role: UserRole?, age: Int?): String? {
    if (role == null) return null
    val roleLabel = when (role) {
        UserRole.PSYCHOLOGIST -> "Psycholog"
        UserRole.PSYCHOLOGY_STUDENT -> "Student Psychologii"
        UserRole.PATIENT -> "Pacjent"
    }
    return if (age != null && age > 0) "$roleLabel • $age lat" else roleLabel
}

fun specializationSet(text: String): Set<String> =
    text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

fun specializationsText(specializations: Set<String>): String = specializations.joinToString(", ")

fun Set<String>.toggled(name: String): Set<String> = if (contains(name)) this - name else this + name

fun <T> List<T>.withoutIndex(index: Int): List<T> = filterIndexed { current, _ -> current != index }
