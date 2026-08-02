package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val messageId: Int = 0,
    val chatId: Int,
    val senderId: Int,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
