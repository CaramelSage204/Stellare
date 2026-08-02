package com.example.data.repository

import com.example.data.local.dao.ChatDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.dao.NoteDao
import com.example.data.local.entity.ChatEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val noteDao: NoteDao
) {
    fun getChatsForUserFlow(userId: Int): Flow<List<ChatEntity>> = chatDao.getChatsForUserFlow(userId)
    suspend fun getChatByParticipants(psychId: Int, patientId: Int): ChatEntity? = chatDao.getChatByParticipants(psychId, patientId)
    suspend fun createChat(chat: ChatEntity): Long = chatDao.insertChat(chat)
    suspend fun updateChat(chat: ChatEntity) = chatDao.updateChat(chat)

    // Messages
    fun getMessagesForChatFlow(chatId: Int): Flow<List<MessageEntity>> = messageDao.getMessagesForChatFlow(chatId)
    suspend fun insertMessage(message: MessageEntity): Long = messageDao.insertMessage(message)

    // Notes
    fun getNotesForChatFlow(chatId: Int): Flow<List<NoteEntity>> = noteDao.getNotesForChatFlow(chatId)
    suspend fun insertNote(note: NoteEntity): Long = noteDao.insertNote(note)
    suspend fun deleteNote(noteId: Int) = noteDao.deleteNote(noteId)
}
