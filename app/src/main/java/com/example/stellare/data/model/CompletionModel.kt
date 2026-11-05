package com.example.stellare.data.model

data class CompletionModel(
    val psychologistId: String = "",
    val patientId: String = "",
    val appointmentId: String = "",
    val completedAt: Long = 0L
) {
    companion object {
        fun idFor(psychologistId: String, patientId: String): String = "${psychologistId}_$patientId"
    }
}
