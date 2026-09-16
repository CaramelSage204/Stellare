package com.example.stellare.data.repository

import com.example.stellare.data.remote.ChatDataSource
import com.example.stellare.data.remote.MessageDataSource
import com.example.stellare.data.remote.NoteDataSource
import com.example.stellare.data.model.ChatModel
import com.example.stellare.data.model.MessageModel
import com.example.stellare.data.model.NoteModel
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val chatDataSource: ChatDataSource,
    private val messageDataSource: MessageDataSource,
    private val noteDataSource: NoteDataSource
) {
    // Chats
    fun getChatsForUserFlow(userId: String): Flow<List<ChatModel>> =
        chatDataSource.getChatsForUser(userId)

    suspend fun getChatByParticipants(psychId: String, patientId: String): ChatModel? =
        chatDataSource.getChatByParticipants(psychId, patientId)

    suspend fun createChat(chat: ChatModel) =
        chatDataSource.saveChat(chat)

    suspend fun updateChat(chat: ChatModel) =
        chatDataSource.updateChat(chat)

    fun newChatId(): String = chatDataSource.newChatId()

    // Messages
    fun getMessagesForChatFlow(chatId: String): Flow<List<MessageModel>> =
        messageDataSource.getMessagesForChat(chatId)

    suspend fun insertMessage(message: MessageModel) =
        messageDataSource.saveMessage(message)

    // Notes
    fun getNotesForChatFlow(chatId: String): Flow<List<NoteModel>> =
        noteDataSource.getNotesForChat(chatId)

    suspend fun insertNote(note: NoteModel) =
        noteDataSource.saveNote(note)

    suspend fun deleteNote(noteId: String) =
        noteDataSource.deleteNote(noteId)
}