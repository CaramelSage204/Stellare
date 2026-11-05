package com.example.stellare.data.model

data class EscrowModel(
    val appointmentId: String = "",
    val patientId: String = "",
    val psychologistId: String = "",
    val amount: Int = 0,
    val status: String = HELD
) {
    companion object {
        const val HELD = "HELD"
        const val RELEASED = "RELEASED"
        const val REFUNDED = "REFUNDED"
    }
}
