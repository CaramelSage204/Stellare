package com.example.stellare.data.remote

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.ChatModel
import com.example.stellare.data.model.FavoriteModel
import com.example.stellare.data.model.MessageModel
import com.example.stellare.data.model.NoteModel
import com.example.stellare.data.model.ReviewModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.WalletTransactionModel
import kotlinx.coroutines.flow.Flow

interface AuthDataSource {
    val currentUserId: Flow<String?>
    fun getCurrentUserIdOnce(): String?
    suspend fun registerWithEmail(email: String, password: String): String?
    suspend fun signInWithEmail(email: String, password: String): String?
    suspend fun signInWithGoogle(idToken: String): String?
    fun signOut()
}

interface UserDataSource {
    fun getUser(uid: String): Flow<UserModel?>
    suspend fun getUserOnce(uid: String): UserModel?
    fun getAllByRole(role: String): Flow<List<UserModel>>
    fun getAllByRoleExcluding(role: String, excludeId: String): Flow<List<UserModel>>
    suspend fun saveUser(user: UserModel)
}

interface FavoriteDataSource {
    fun getFavoritesForUser(userId: String): Flow<List<FavoriteModel>>
    suspend fun saveFavorite(favorite: FavoriteModel)
    suspend fun deleteFavorite(userId: String, psychologistId: String)
}

interface ChatDataSource {
    fun getChatsForUser(userId: String): Flow<List<ChatModel>>
    suspend fun getChatByParticipants(psychId: String, patientId: String): ChatModel?
    suspend fun saveChat(chat: ChatModel)
    suspend fun updateChat(chat: ChatModel)
    fun newChatId(): String
}

interface MessageDataSource {
    fun getMessagesForChat(chatId: String): Flow<List<MessageModel>>
    suspend fun saveMessage(message: MessageModel)
}

interface NoteDataSource {
    fun getNotesForChat(chatId: String): Flow<List<NoteModel>>
    suspend fun saveNote(note: NoteModel)
    suspend fun deleteNote(noteId: String)
}

interface AppointmentDataSource {
    fun getAppointmentsForPsychologist(psychologistId: String): Flow<List<AppointmentModel>>
    fun getAppointmentsForPatient(patientId: String): Flow<List<AppointmentModel>>
    fun getFreeSlotsForPsychologist(psychologistId: String): Flow<List<AppointmentModel>>
    suspend fun saveAppointment(appointment: AppointmentModel)
    suspend fun bookAppointmentAtomically(
        appointmentId: String,
        patientId: String,
        psychologistId: String,
        priceCoins: Int,
        notes: String
    )
    suspend fun cancelBooking(appointmentId: String)
    suspend fun completeAppointment(appointmentId: String)
    suspend fun deleteAppointment(appointmentId: String)
    suspend fun getAppointmentByDateTime(psychId: String, date: String, time: String): AppointmentModel?
}

interface WalletDataSource {
    fun getTransactionsForUser(userId: String): Flow<List<WalletTransactionModel>>
    suspend fun saveTransaction(transaction: WalletTransactionModel)
}

interface ReviewDataSource {
    fun getReviewsForPsychologist(psychologistId: String): Flow<List<ReviewModel>>
    suspend fun saveReview(review: ReviewModel)
    suspend fun saveReviewWithRating(
        review: ReviewModel,
        psychologistId: String,
        newRating: Double,
        newRatingCount: Int
    )
}
