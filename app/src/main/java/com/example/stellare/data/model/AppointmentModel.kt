package com.example.stellare.data.model

data class AppointmentModel(
    val appointmentId: String = "",
    val psychologistId: String = "",
    val patientId: String? = null,
    val date: String = "",
    val time: String = "",
    val notes: String = "",
    val status: String = "FREE"
)
