package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE psychologistId = :userId OR patientId = :userId ORDER BY lastMessageTime DESC")
    fun getChatsForUserFlow(userId: Int): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE psychologistId = :psychId AND patientId = :patientId LIMIT 1")
    suspend fun getChatByParticipants(psychId: Int, patientId: Int): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity): Long

    @Update
    suspend fun updateChat(chat: ChatEntity)
}
