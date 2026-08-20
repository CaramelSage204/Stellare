package com.example.stellare.data.model

data class ChatModel(
    val chatId: String = "",
    val psychologistId: String = "",
    val patientId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L
)
