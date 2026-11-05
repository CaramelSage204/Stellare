package com.example.stellare.data.remote

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.CompletionOutcome
import com.example.stellare.data.model.ChatModel
import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.FavoriteModel
import com.example.stellare.data.model.MessageModel
import com.example.stellare.data.model.NoteModel
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PrivateProfile
import com.example.stellare.data.model.RatingModel
import com.example.stellare.data.model.ReviewModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.VerificationRequestModel
import com.example.stellare.data.model.WalletTransactionModel
import kotlinx.coroutines.flow.Flow

interface AuthDataSource {
    val currentUserId: Flow<String?>
    fun getCurrentUserIdOnce(): String?
    fun getCurrentUserEmailOnce(): String? = null
    fun getCurrentUserDisplayNameOnce(): String? = null
    suspend fun registerWithEmail(email: String, password: String): String?
    suspend fun signInWithEmail(email: String, password: String): String?
    suspend fun signInWithGoogle(idToken: String): String?
    suspend fun signInWithFacebook(): String? = null
    suspend fun sendPasswordResetEmail(email: String)
    fun getCurrentUserPhoneNumberOnce(): String? = null
    suspend fun requestPhoneCode(phoneNumber: String): PhoneCodeRequest? = null
    suspend fun confirmPhoneCode(verificationId: String, code: String): String? = null
    suspend fun lastSignInMillis(): Long? = null
    suspend fun deleteCurrentAccount()
    fun signOut()
}

interface UserDataSource {
    fun getUser(uid: String): Flow<CurrentUserRead>
    suspend fun getUserOnce(uid: String): UserModel?
    fun getAllByRole(role: String): Flow<List<UserModel>>
    fun getAllByRoleExcluding(role: String, excludeId: String): Flow<List<UserModel>>
    suspend fun saveUser(user: UserModel)
    suspend fun deleteUser(uid: String)
}

interface PrivateProfileDataSource {
    fun getPrivateProfile(uid: String): Flow<PrivateProfile?>
    suspend fun createPrivateProfile(uid: String, phone: String, email: String)
    suspend fun savePhone(uid: String, phone: String)
    suspend fun saveEmail(uid: String, email: String)
    suspend fun deletePrivateProfile(uid: String)
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
    suspend fun cancelBooking(appointmentId: String, cancelledBy: String): CancelOutcome
    suspend fun refundAbandonedBooking(appointmentId: String, patientId: String): CancelOutcome
    suspend fun completeAppointment(appointmentId: String): CompletionOutcome
    suspend fun deleteAppointment(appointmentId: String)
    suspend fun getAppointmentByDateTime(psychId: String, date: String, time: String): AppointmentModel?
    suspend fun bookingLanded(appointmentId: String, patientId: String): Boolean
    suspend fun completionLanded(appointmentId: String): CompletionOutcome?
    suspend fun cancellationLanded(appointmentId: String, cancelledBy: String): CancelOutcome?
    suspend fun reclaimLanded(appointmentId: String): CancelOutcome?
}

interface EscrowDataSource {
    fun observeEscrow(appointmentId: String): Flow<EscrowModel?>
}

interface WalletDataSource {
    fun getTransactionsForUser(userId: String): Flow<List<WalletTransactionModel>>
    suspend fun saveTransaction(transaction: WalletTransactionModel)
    suspend fun topUp(userId: String, amount: Int, title: String, timestamp: Long, entryId: String)
    suspend fun topUpLanded(userId: String, entryId: String): Boolean
}

interface ReviewDataSource {
    fun getReviewsForPsychologist(psychologistId: String): Flow<List<ReviewModel>>
    suspend fun hasCompletedSession(psychologistId: String, patientId: String): Boolean
    suspend fun saveReviewWithRating(review: ReviewModel, psychologistId: String)
}

interface RatingDataSource {
    fun getRatings(): Flow<Map<String, RatingModel>>
    fun getRating(psychologistId: String): Flow<RatingModel>
}

interface PostDataSource {
    fun getActivePosts(type: String): Flow<List<PostModel>>
    fun getPostsByAuthor(authorId: String): Flow<List<PostModel>>
    suspend fun savePost(post: PostModel)
    suspend fun deactivatePost(postId: String)
    suspend fun deletePost(postId: String)
    fun newPostId(): String
}

interface VerificationDataSource {
    fun myRequest(uid: String): Flow<VerificationRequestModel?>
    fun pendingRequests(): Flow<List<VerificationRequestModel>>
    suspend fun submit(request: VerificationRequestModel)
    suspend fun decide(uid: String, approved: Boolean, note: String)
    suspend fun deleteRequest(uid: String)
}
