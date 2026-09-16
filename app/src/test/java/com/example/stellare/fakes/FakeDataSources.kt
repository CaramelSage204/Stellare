package com.example.stellare.fakes

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.ChatModel
import com.example.stellare.data.model.FavoriteModel
import com.example.stellare.data.model.MessageModel
import com.example.stellare.data.model.NoteModel
import com.example.stellare.data.model.ReviewModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.data.remote.AppointmentDataSource
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.remote.ChatDataSource
import com.example.stellare.data.remote.FavoriteDataSource
import com.example.stellare.data.remote.MessageDataSource
import com.example.stellare.data.remote.NoteDataSource
import com.example.stellare.data.remote.ReviewDataSource
import com.example.stellare.data.remote.UserDataSource
import com.example.stellare.data.remote.WalletDataSource
import com.example.stellare.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class FakeAuthDataSource(initialUserId: String? = null) : AuthDataSource {
    val userId = MutableStateFlow(initialUserId)

    var registerResult: Result<String?> = Result.success(null)
    var signInResult: Result<String?> = Result.success(null)
    var signInWithGoogleResult: Result<String?> = Result.success(null)
    var signOutCalled = false
        private set

    override val currentUserId: Flow<String?> = userId

    override fun getCurrentUserIdOnce(): String? = userId.value

    override suspend fun registerWithEmail(email: String, password: String): String? =
        registerResult.getOrThrow()?.also { userId.value = it }

    override suspend fun signInWithEmail(email: String, password: String): String? =
        signInResult.getOrThrow()?.also { userId.value = it }

    override suspend fun signInWithGoogle(idToken: String): String? =
        signInWithGoogleResult.getOrThrow()?.also { userId.value = it }

    override fun signOut() {
        signOutCalled = true
        userId.value = null
    }
}

class FakeUserDataSource(initial: List<UserModel> = emptyList()) : UserDataSource {
    val users = MutableStateFlow(initial)

    var getAllByRoleSubscriptionCount = 0
        private set

    override fun getUser(uid: String): Flow<UserModel?> =
        users.map { list -> list.firstOrNull { it.id == uid } }

    override suspend fun getUserOnce(uid: String): UserModel? =
        users.value.firstOrNull { it.id == uid }

    override fun getAllByRole(role: String): Flow<List<UserModel>> =
        users.map { list -> list.filter { it.role.name == role } }
            .onStart { getAllByRoleSubscriptionCount++ }

    override fun getAllByRoleExcluding(role: String, excludeId: String): Flow<List<UserModel>> =
        users.map { list -> list.filter { it.role.name == role && it.id != excludeId } }

    override suspend fun saveUser(user: UserModel) {
        users.value = users.value.filterNot { it.id == user.id } + user
    }
}

class FakeFavoriteDataSource : FavoriteDataSource {
    val favorites = MutableStateFlow<List<FavoriteModel>>(emptyList())

    override fun getFavoritesForUser(userId: String): Flow<List<FavoriteModel>> =
        favorites.map { list -> list.filter { it.userId == userId } }

    override suspend fun saveFavorite(favorite: FavoriteModel) {
        favorites.value = favorites.value.filterNot {
            it.userId == favorite.userId && it.psychologistId == favorite.psychologistId
        } + favorite
    }

    override suspend fun deleteFavorite(userId: String, psychologistId: String) {
        favorites.value = favorites.value.filterNot {
            it.userId == userId && it.psychologistId == psychologistId
        }
    }
}

class FakeChatDataSource : ChatDataSource {
    val chats = MutableStateFlow<List<ChatModel>>(emptyList())
    private var licznik = 0

    override fun getChatsForUser(userId: String): Flow<List<ChatModel>> =
        chats.map { list -> list.filter { it.participants.contains(userId) } }

    override suspend fun getChatByParticipants(psychId: String, patientId: String): ChatModel? =
        chats.value.firstOrNull {
            it.participants.contains(psychId) && it.participants.contains(patientId)
        }

    override suspend fun saveChat(chat: ChatModel) {
        chats.value = chats.value.filterNot { it.chatId == chat.chatId } + chat
    }

    override suspend fun updateChat(chat: ChatModel) {
        val existingChat = chats.value.firstOrNull { it.chatId == chat.chatId }
            ?: throw IllegalStateException("Czat nie istnieje: ${chat.chatId}")
        chats.value = chats.value.filterNot { it.chatId == chat.chatId } +
            existingChat.copy(lastMessage = chat.lastMessage, lastMessageTime = chat.lastMessageTime)
    }

    override fun newChatId(): String = "czat-${++licznik}"
}

class FakeMessageDataSource : MessageDataSource {
    val messages = MutableStateFlow<List<MessageModel>>(emptyList())

    override fun getMessagesForChat(chatId: String): Flow<List<MessageModel>> =
        messages.map { list -> list.filter { it.chatId == chatId } }

    override suspend fun saveMessage(message: MessageModel) {
        messages.value = messages.value.filterNot { it.messageId == message.messageId } + message
    }
}

class FakeNoteDataSource : NoteDataSource {
    val notes = MutableStateFlow<List<NoteModel>>(emptyList())

    override fun getNotesForChat(chatId: String): Flow<List<NoteModel>> =
        notes.map { list -> list.filter { it.chatId == chatId } }

    override suspend fun saveNote(note: NoteModel) {
        notes.value = notes.value.filterNot { it.noteId == note.noteId } + note
    }

    override suspend fun deleteNote(noteId: String) {
        notes.value = notes.value.filterNot { it.noteId == noteId }
    }
}

/**
 * Atrapa źródła terminów. Bez podanych [users] i [wallet] pomija sprawdzanie salda
 * i księgowanie — rezerwacja uda się niezależnie od stanu portfela. Testy dotyczące
 * salda lub księgi MUSZĄ przekazać obie atrapy, inaczej nie sprawdzą niczego.
 */
class FakeAppointmentDataSource(
    initial: List<AppointmentModel> = emptyList(),
    private val users: FakeUserDataSource? = null,
    private val wallet: FakeWalletDataSource? = null
) : AppointmentDataSource {
    val appointments = MutableStateFlow(initial)

    override fun getAppointmentsForPsychologist(psychologistId: String): Flow<List<AppointmentModel>> =
        appointments.map { list -> list.filter { it.psychologistId == psychologistId } }

    override fun getAppointmentsForPatient(patientId: String): Flow<List<AppointmentModel>> =
        appointments.map { list -> list.filter { it.patientId == patientId } }

    override fun getFreeSlotsForPsychologist(psychologistId: String): Flow<List<AppointmentModel>> =
        appointments.map { list ->
            list.filter { it.psychologistId == psychologistId && it.status == "FREE" }
        }

    override suspend fun saveAppointment(appointment: AppointmentModel) {
        appointments.value = appointments.value.filterNot { it.appointmentId == appointment.appointmentId } + appointment
    }

    override suspend fun bookAppointmentAtomically(
        appointmentId: String,
        patientId: String,
        psychologistId: String,
        priceCoins: Int,
        notes: String
    ) {
        val appointment = appointments.value.firstOrNull { it.appointmentId == appointmentId }
        if (appointment == null || appointment.status != "FREE") {
            throw IllegalStateException("Termin został już zarezerwowany.")
        }

        val patient = users?.users?.value?.firstOrNull { it.id == patientId }
        if (users != null) {
            val patientBalance = patient?.coinsBalance ?: 0
            if (patientBalance < priceCoins) {
                throw IllegalStateException("Za mało monet w portfelu.")
            }
        }

        appointments.value = appointments.value.map {
            if (it.appointmentId == appointmentId) {
                it.copy(patientId = patientId, status = "BOOKED", notes = notes)
            } else {
                it
            }
        }

        patient?.let { existingPatient ->
            users?.let { userSource ->
                userSource.saveUser(
                    existingPatient.copy(
                        coinsBalance = existingPatient.coinsBalance - priceCoins,
                        lastTransferTo = psychologistId,
                        lastTransferAmount = priceCoins
                    )
                )
                val psychologist = userSource.users.value.firstOrNull { it.id == psychologistId }
                if (psychologist != null) {
                    userSource.saveUser(psychologist.copy(coinsBalance = psychologist.coinsBalance + priceCoins))
                }
            }
        }

        if (wallet != null) {
            val now = System.currentTimeMillis()
            wallet.saveTransaction(
                WalletTransactionModel(
                    transactionId = "${appointmentId}_payment_$now",
                    userId = patientId,
                    counterpartyId = psychologistId,
                    appointmentId = appointmentId,
                    amount = -priceCoins,
                    type = WalletTransactionType.BOOKING_PAYMENT,
                    title = "Opłata za wizytę",
                    timestamp = now
                )
            )
            wallet.saveTransaction(
                WalletTransactionModel(
                    transactionId = "${appointmentId}_$psychologistId",
                    userId = psychologistId,
                    counterpartyId = patientId,
                    appointmentId = appointmentId,
                    amount = priceCoins,
                    type = WalletTransactionType.BOOKING_EARNING,
                    title = "Wpływ za wizytę",
                    timestamp = now
                )
            )
        }
    }

    override suspend fun cancelBooking(appointmentId: String) {
        appointments.value = appointments.value.map {
            if (it.appointmentId == appointmentId) {
                it.copy(patientId = null, status = "FREE", notes = "")
            } else {
                it
            }
        }
    }

    override suspend fun completeAppointment(appointmentId: String) {
        appointments.value = appointments.value.map {
            if (it.appointmentId == appointmentId) it.copy(status = "COMPLETED") else it
        }
    }

    override suspend fun deleteAppointment(appointmentId: String) {
        appointments.value = appointments.value.filterNot { it.appointmentId == appointmentId }
    }

    override suspend fun getAppointmentByDateTime(psychId: String, date: String, time: String): AppointmentModel? =
        appointments.value.firstOrNull {
            it.psychologistId == psychId && it.date == date && it.time == time
        }
}

class FakeWalletDataSource : WalletDataSource {
    val transactions = MutableStateFlow<List<WalletTransactionModel>>(emptyList())

    override fun getTransactionsForUser(userId: String): Flow<List<WalletTransactionModel>> =
        transactions.map { list -> list.filter { it.userId == userId } }

    override suspend fun saveTransaction(transaction: WalletTransactionModel) {
        transactions.value = transactions.value.filterNot { it.transactionId == transaction.transactionId } + transaction
    }
}

class FakeReviewDataSource(
    private val userDataSource: FakeUserDataSource
) : ReviewDataSource {
    val reviews = MutableStateFlow<List<ReviewModel>>(emptyList())
    var lastRatingUpdate: Pair<Double, Int>? = null
        private set

    override fun getReviewsForPsychologist(psychologistId: String): Flow<List<ReviewModel>> =
        reviews.map { list -> list.filter { it.psychologistId == psychologistId } }

    override suspend fun saveReview(review: ReviewModel) {
        reviews.value = reviews.value.filterNot { it.reviewId == review.reviewId } + review
    }

    override suspend fun saveReviewWithRating(
        review: ReviewModel,
        psychologistId: String,
        newRating: Double,
        newRatingCount: Int
    ) {
        reviews.value = reviews.value.filterNot { it.reviewId == review.reviewId } + review
        lastRatingUpdate = newRating to newRatingCount
        val psychologist = userDataSource.users.value.firstOrNull { it.id == psychologistId }
        if (psychologist != null) {
            userDataSource.saveUser(psychologist.copy(rating = newRating, ratingCount = newRatingCount))
        }
    }
}

/**
 * Buduje [UserRepository] oparte wyłącznie na atrapach, z zalogowanym użytkownikiem [uid]
 * mającym saldo [coinsBalance] monet (domyślnie 500). Testy sprawdzające saldo lub księgę
 * (np. rezerwację bez pokrycia) powinny przekazać własny, współdzielony [userDataSource] —
 * ten sam obiekt trzeba wtedy podać jako `users` do [FakeAppointmentDataSource], inaczej
 * sprawdzanie salda zostanie pominięte.
 */
fun fakeUserRepository(
    uid: String,
    coinsBalance: Int = 500,
    userDataSource: FakeUserDataSource = FakeUserDataSource()
): UserRepository {
    userDataSource.users.value = userDataSource.users.value.filterNot { it.id == uid } +
        UserModel(id = uid, coinsBalance = coinsBalance)
    return UserRepository(
        FakeAuthDataSource(uid),
        userDataSource,
        FakeFavoriteDataSource()
    )
}
