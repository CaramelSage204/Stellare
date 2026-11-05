package com.example.stellare.ui.psychologistdetail

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.ui.PolishPlural

data class SlotOption(
    val label: String,
    val isBookable: Boolean,
    val isSelected: Boolean
)

enum class ReviewFooter { OwnReview, Composer, Nothing }

fun slotOption(appointment: AppointmentModel, selectedId: String?): SlotOption {
    val isBookable = appointment.priceCoins > 0
    val parts = appointment.date.split("-")
    val shortDate = if (parts.size == 3) "${parts[2]}.${parts[1]}" else appointment.date
    val label = "$shortDate o ${appointment.time}"
    return SlotOption(
        label = if (isBookable) label else "$label (bez ceny)",
        isBookable = isBookable,
        isSelected = isBookable && selectedId == appointment.appointmentId
    )
}

fun bookingDateLabel(date: String): String {
    val parts = date.split("-")
    return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else date
}

fun priceLabel(pricePerSession: Double): String = priceLabel(pricePerSession.toInt())

fun priceLabel(pricePerSession: Int): String = "$pricePerSession WKT / sesja"

fun headerRatingText(summary: ReviewSummary): String = when {
    summary.hasReviews -> "${"%.1f".format(summary.average)} (${summary.count})"
    summary.isLoading -> "Wczytywanie…"
    summary.isFailed -> "Oceny niedostępne"
    else -> "Brak ocen"
}

fun ratingCaption(summary: ReviewSummary): String = when {
    summary.hasReviews -> PolishPlural.opinions(summary.count)
    summary.isLoading -> "Wczytywanie…"
    summary.failureMessage != null -> summary.failureMessage
    else -> "Brak opinii"
}

fun reviewFooter(hasOwnReview: Boolean, hasCompletedSession: Boolean): ReviewFooter = when {
    hasOwnReview -> ReviewFooter.OwnReview
    hasCompletedSession -> ReviewFooter.Composer
    else -> ReviewFooter.Nothing
}

fun hasUnpricedSlots(slots: List<AppointmentModel>): Boolean = slots.any { it.priceCoins <= 0 }

fun canAffordBooking(balance: Int, cost: Int): Boolean = balance >= cost
