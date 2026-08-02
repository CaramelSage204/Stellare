package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow

class WektorRepository(private val db: AppDatabase) {
    val userDao = db.userDao()
    val chatDao = db.chatDao()
    val messageDao = db.messageDao()
    val noteDao = db.noteDao()
    val reviewDao = db.reviewDao()
    val favoriteDao = db.favoriteDao()
    val appointmentDao = db.appointmentDao()
    val walletTransactionDao = db.walletTransactionDao()

    // Users
    fun getCurrentUserFlow(): Flow<UserEntity?> = userDao.getCurrentUserFlow()
    suspend fun getCurrentUser(): UserEntity? = userDao.getCurrentUser()
    fun getUsersFlow(role: String, currentUserId: Int): Flow<List<UserEntity>> = userDao.getUsersFlow(role, currentUserId)
    fun getUsersOfRoleFlow(role: String): Flow<List<UserEntity>> = userDao.getUsersOfRoleFlow(role)
    suspend fun getUserById(userId: Int): UserEntity? = userDao.getUserById(userId)
    fun getUserByIdFlow(userId: Int): Flow<UserEntity?> = userDao.getUserByIdFlow(userId)
    suspend fun insertUser(user: UserEntity): Long = userDao.insertUser(user)
    suspend fun updateUser(user: UserEntity) = userDao.updateUser(user)
    suspend fun clearCurrentUser() = userDao.clearCurrentUser()

    // Favorites
    fun getFavoritesForUserFlow(userId: Int): Flow<List<FavoriteEntity>> = favoriteDao.getFavoritesForUserFlow(userId)
    suspend fun insertFavorite(userId: Int, psychologistId: Int) = favoriteDao.insertFavorite(FavoriteEntity(userId, psychologistId))
    suspend fun deleteFavorite(userId: Int, psychologistId: Int) = favoriteDao.deleteFavorite(userId, psychologistId)

    // Chats
    fun getChatsForUserFlow(userId: Int): Flow<List<ChatEntity>> = chatDao.getChatsForUserFlow(userId)
    suspend fun getChatByParticipants(psychId: Int, patientId: Int): ChatEntity? = chatDao.getChatByParticipants(psychId, patientId)
    suspend fun createChat(chat: ChatEntity): Long = chatDao.insertChat(chat)
    suspend fun updateChat(chat: ChatEntity) = chatDao.updateChat(chat)

    // Messages
    fun getMessagesForChatFlow(chatId: Int): Flow<List<MessageEntity>> = messageDao.getMessagesForChatFlow(chatId)
    suspend fun insertMessage(message: MessageEntity): Long = messageDao.insertMessage(message)

    // Notes (Zametki)
    fun getNotesForChatFlow(chatId: Int): Flow<List<NoteEntity>> = noteDao.getNotesForChatFlow(chatId)
    suspend fun insertNote(note: NoteEntity): Long = noteDao.insertNote(note)
    suspend fun deleteNote(noteId: Int) = noteDao.deleteNote(noteId)

    // Reviews
    fun getReviewsForPsychologistFlow(psychologistId: Int): Flow<List<ReviewEntity>> = reviewDao.getReviewsForPsychologistFlow(psychologistId)
    suspend fun insertReview(review: ReviewEntity): Long = reviewDao.insertReview(review)

    // Appointments (Kalendarz)
    fun getAppointmentsForPsychologistFlow(psychId: Int): Flow<List<AppointmentEntity>> = appointmentDao.getAppointmentsForPsychologist(psychId)
    fun getAppointmentsForPatientFlow(patientId: Int): Flow<List<AppointmentEntity>> = appointmentDao.getAppointmentsForPatient(patientId)
    fun getFreeSlotsForPsychologistFlow(psychId: Int): Flow<List<AppointmentEntity>> = appointmentDao.getFreeSlotsForPsychologist(psychId)
    suspend fun insertAppointment(appointment: AppointmentEntity): Long = appointmentDao.insertAppointment(appointment)
    suspend fun bookAppointment(appointmentId: Int, patientId: Int, notes: String) = appointmentDao.bookAppointment(appointmentId, patientId, notes)
    suspend fun cancelBooking(appointmentId: Int) = appointmentDao.cancelBooking(appointmentId)
    suspend fun completeAppointment(appointmentId: Int) = appointmentDao.completeAppointment(appointmentId)
    suspend fun deleteAppointment(appointmentId: Int) = appointmentDao.deleteAppointment(appointmentId)
    suspend fun getAppointmentByDateTime(psychId: Int, date: String, time: String): AppointmentEntity? = appointmentDao.getAppointmentByDateTime(psychId, date, time)

    // Wallet Transactions
    fun getWalletTransactionsFlow(userId: Int): Flow<List<WalletTransactionEntity>> = walletTransactionDao.getTransactionsForUserFlow(userId)
    suspend fun insertWalletTransaction(transaction: WalletTransactionEntity): Long = walletTransactionDao.insertTransaction(transaction)
}
