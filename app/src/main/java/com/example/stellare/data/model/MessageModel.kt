package com.example.stellare.data.model

data class MessageModel(
    val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)
