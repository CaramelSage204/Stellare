package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val chatId: Int = 0,
    val psychologistId: Int,
    val patientId: Int,
    val lastMessage: String = "",
    val lastMessageTime: Long = System.currentTimeMillis()
)
