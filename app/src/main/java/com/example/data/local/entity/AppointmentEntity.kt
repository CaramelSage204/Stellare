package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val appointmentId: Int = 0,
    val psychologistId: Int,
    val patientId: Int? = null,
    val date: String, // format: "yyyy-MM-dd" e.g., "2026-07-10"
    val time: String, // format: "HH:mm" e.g., "14:00"
    val notes: String = "",
    val status: String = "FREE" // "FREE", "BOOKED", "COMPLETED"
)
