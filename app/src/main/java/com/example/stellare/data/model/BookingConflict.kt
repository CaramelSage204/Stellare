package com.example.stellare.data.model

enum class BookingConflict {
    Free,
    BookedByRequester,
    TakenByOther;

    companion object {
        const val SLOT_TAKEN_MESSAGE = "Termin został już zarezerwowany."

        fun classify(status: String?, bookedPatientId: String?, requesterId: String): BookingConflict = when {
            status == "FREE" -> Free
            status == "BOOKED" && bookedPatientId == requesterId -> BookedByRequester
            else -> TakenByOther
        }
    }
}
