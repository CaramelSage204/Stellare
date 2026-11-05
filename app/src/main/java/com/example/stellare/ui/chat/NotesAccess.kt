package com.example.stellare.ui.chat

import com.example.stellare.data.model.ChatModel

fun canSeeNotesTab(chat: ChatModel?, userId: String?): Boolean =
    chat != null && !userId.isNullOrEmpty() && chat.psychologistId == userId
