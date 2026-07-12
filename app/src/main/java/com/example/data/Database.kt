package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ------------------ ENTITIES ------------------

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firstName: String,
    val lastName: String,
    val age: Int,
    val gender: String,
    val phone: String,
    val email: String,
    val role: String, // "PSYCHOLOGIST" or "PATIENT"
    val isVerified: Boolean = false,
    val qualifications: String = "", // e.g. "Licencjat", "Doktorant", "Certyfikat"
    val specializations: String = "", // e.g. "Rodzina, Ogólny, Praca"
    val pricePerSession: Double = 0.0,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val bio: String = "",
    val isCurrentUser: Boolean = false,
    val customPrices: String = "",
    val profileImageUri: String? = null,
    val coinsBalance: Int = 100
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val chatId: Int = 0,
    val psychologistId: Int,
    val patientId: Int,
    val lastMessage: String = "",
    val lastMessageTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val messageId: Int = 0,
    val chatId: Int,
    val senderId: Int,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val noteId: Int = 0,
    val chatId: Int,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val reviewId: Int = 0,
    val psychologistId: Int,
    val reviewerName: String,
    val rating: Int,
    val comment: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites", primaryKeys = ["userId", "psychologistId"])
data class FavoriteEntity(
    val userId: Int,
    val psychologistId: Int
)

// ------------------ DAOS ------------------

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    fun getCurrentUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    @Query("SELECT * FROM users WHERE role = :role AND id != :currentUserId")
    fun getUsersFlow(role: String, currentUserId: Int): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE role = :role")
    fun getUsersOfRoleFlow(role: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Int): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun getUserByIdFlow(userId: Int): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET isCurrentUser = 0")
    suspend fun clearCurrentUser()

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Int)
}

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

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChatFlow(chatId: Int): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun getNotesForChatFlow(chatId: Int): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE noteId = :noteId")
    suspend fun deleteNote(noteId: Int)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE psychologistId = :psychologistId ORDER BY timestamp DESC")
    fun getReviewsForPsychologistFlow(psychologistId: Int): Flow<List<ReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: ReviewEntity): Long
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE userId = :userId")
    fun getFavoritesForUserFlow(userId: Int): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE userId = :userId AND psychologistId = :psychologistId")
    suspend fun deleteFavorite(userId: Int, psychologistId: Int)
}

@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val appointmentId: Int = 0,
    val psychologistId: Int,
    val patientId: Int? = null,
    val date: String, // format: "yyyy-MM-dd" e.g., "2026-07-10"
    val time: String, // format: "HH:mm" e.g., "14:00"
    val notes: String = "",
    val status: String = "FREE" // "FREE", "BOOKED", "COMPLETED"
)

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments WHERE psychologistId = :psychologistId ORDER BY date ASC, time ASC")
    fun getAppointmentsForPsychologist(psychologistId: Int): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE patientId = :patientId ORDER BY date ASC, time ASC")
    fun getAppointmentsForPatient(patientId: Int): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE psychologistId = :psychologistId AND status = 'FREE' ORDER BY date ASC, time ASC")
    fun getFreeSlotsForPsychologist(psychologistId: Int): Flow<List<AppointmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: AppointmentEntity): Long

    @Query("UPDATE appointments SET patientId = :patientId, status = 'BOOKED', notes = :notes WHERE appointmentId = :appointmentId")
    suspend fun bookAppointment(appointmentId: Int, patientId: Int, notes: String)

    @Query("UPDATE appointments SET patientId = NULL, status = 'FREE', notes = '' WHERE appointmentId = :appointmentId")
    suspend fun cancelBooking(appointmentId: Int)

    @Query("UPDATE appointments SET status = 'COMPLETED' WHERE appointmentId = :appointmentId")
    suspend fun completeAppointment(appointmentId: Int)

    @Query("DELETE FROM appointments WHERE appointmentId = :appointmentId")
    suspend fun deleteAppointment(appointmentId: Int)

    @Query("SELECT * FROM appointments WHERE psychologistId = :psychologistId AND date = :date AND time = :time LIMIT 1")
    suspend fun getAppointmentByDateTime(psychologistId: Int, date: String, time: String): AppointmentEntity?
}

@Entity(tableName = "wallet_transactions")
data class WalletTransactionEntity(
    @PrimaryKey(autoGenerate = true) val transactionId: Int = 0,
    @ColumnInfo(name = "userId") val userId: Int,
    @ColumnInfo(name = "amount") val amount: Int, // e.g. +50, -30
    @ColumnInfo(name = "title") val title: String, // e.g., "Doładowanie", "Opłata za konsultację"
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface WalletTransactionDao {
    @Query("SELECT * FROM wallet_transactions WHERE userId = :userId ORDER BY timestamp DESC")
    fun getTransactionsForUserFlow(userId: Int): Flow<List<WalletTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: WalletTransactionEntity): Long
}

// ------------------ DATABASE ------------------

@Database(
    entities = [
        UserEntity::class,
        ChatEntity::class,
        MessageEntity::class,
        NoteEntity::class,
        ReviewEntity::class,
        FavoriteEntity::class,
        AppointmentEntity::class,
        WalletTransactionEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun noteDao(): NoteDao
    abstract fun reviewDao(): ReviewDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun walletTransactionDao(): WalletTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wektor_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ------------------ REPOSITORY ------------------

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
