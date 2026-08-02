package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val noteId: Int = 0,
    val chatId: Int,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
