package com.example.stellare.data.model

data class NoteModel(
    val noteId: String = "",
    val chatId: String = "",
    val title: String = "",
    val content: String = "",
    val timestamp: Long = 0L
)
