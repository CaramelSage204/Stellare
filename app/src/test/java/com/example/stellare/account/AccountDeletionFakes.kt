package com.example.stellare.account

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.CompletionOutcome
import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.ChatModel
import com.example.stellare.data.model.NoteModel
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PrivateProfile
import com.example.stellare.data.model.RecentLoginRequiredException
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.VerificationRequestModel
import com.example.stellare.data.remote.AppointmentDataSource
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.remote.ChatDataSource
import com.example.stellare.data.remote.NoteDataSource
import com.example.stellare.data.remote.PostDataSource
import com.example.stellare.data.remote.PrivateProfileDataSource
import com.example.stellare.data.remote.UserDataSource
import com.example.stellare.data.remote.UserDocumentWatch
import com.example.stellare.data.remote.VerificationDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class EventLog {
    val events = mutableListOf<String>()
    fun add(event: String) {
        events += event
    }
}

class AccountAuthDataSource(
    private val log: EventLog,
    uid: String? = "u1",
    var lastSignIn: Long? = NOW,
    private val deferredSignOutScope: CoroutineScope? = null
) : AuthDataSource {
    val userId = MutableStateFlow(uid)
    var deleteFailure: Exception? = null
    var signOutCount = 0
        private set

    override val currentUserId: Flow<String?> = userId
    override fun getCurrentUserIdOnce(): String? = userId.value
    override suspend fun lastSignInMillis(): Long? = lastSignIn
    override suspend fun registerWithEmail(email: String, password: String): String? = null
    override suspend fun signInWithEmail(email: String, password: String): String? = null
    override suspend fun signInWithGoogle(idToken: String): String? = null

    override suspend fun sendPasswordResetEmail(email: String) = throw UnsupportedOperationException()

    override suspend fun deleteCurrentAccount() {
        log.add("auth:delete")
        deleteFailure?.let { throw it }
        val scope = deferredSignOutScope
        if (scope == null) {
            userId.value = null
        } else {
            scope.launch {
                delay(DEFERRED_SIGN_OUT_MS)
                userId.value = null
            }
        }
    }

    override fun signOut() {
        signOutCount++
        userId.value = null
    }

    companion object {
        const val NOW = 1_000_000_000L
        const val DEFERRED_SIGN_OUT_MS = 50L
    }
}

class AccountUserDataSource(private val log: EventLog, initial: List<UserModel> = emptyList()) : UserDataSource {
    val users = MutableStateFlow(initial)
    var deleteFailure: Exception? = null

    override fun getUser(uid: String): Flow<CurrentUserRead> = flow {
        val watch = UserDocumentWatch()
        users.collect { list ->
            val document = list.firstOrNull { it.id == uid }
            emit(watch.read(user = document, exists = document != null, fromCache = false, error = null))
        }
    }
    override suspend fun getUserOnce(uid: String): UserModel? = users.value.firstOrNull { it.id == uid }
    override fun getAllByRole(role: String): Flow<List<UserModel>> = users
    override fun getAllByRoleExcluding(role: String, excludeId: String): Flow<List<UserModel>> = users
    override suspend fun saveUser(user: UserModel) {
        users.value = users.value.filterNot { it.id == user.id } + user
    }

    override suspend fun deleteUser(uid: String) {
        log.add("user:delete")
        deleteFailure?.let { throw it }
        users.value = users.value.filterNot { it.id == uid }
    }
}

class AccountPrivateProfileDataSource(
    private val log: EventLog,
    initial: Map<String, PrivateProfile> = mapOf("u1" to PrivateProfile(phone = "500600700", email = "u1@example.com", coinsBalance = 40))
) : PrivateProfileDataSource {
    val profiles = MutableStateFlow(initial)
    var deleteFailure: Exception? = null

    override fun getPrivateProfile(uid: String): Flow<PrivateProfile?> = profiles.map { it[uid] }
    override suspend fun createPrivateProfile(uid: String, phone: String, email: String) = Unit
    override suspend fun savePhone(uid: String, phone: String) = Unit
    override suspend fun saveEmail(uid: String, email: String) = Unit

    override suspend fun deletePrivateProfile(uid: String) {
        log.add("private:delete")
        deleteFailure?.let { throw it }
        profiles.value = profiles.value - uid
    }
}

class AccountPostDataSource(private val log: EventLog, initial: List<PostModel> = emptyList()) : PostDataSource {
    val posts = MutableStateFlow(initial)
    val failingIds = mutableSetOf<String>()
    var readFailure: Exception? = null
    var readHangs = false
    val hangingIds = mutableSetOf<String>()

    override fun getActivePosts(type: String): Flow<List<PostModel>> = posts
    override fun getPostsByAuthor(authorId: String): Flow<List<PostModel>> = flow {
        readFailure?.let { throw it }
        if (readHangs) awaitCancellation()
        emit(posts.value.filter { it.authorId == authorId })
    }

    override suspend fun savePost(post: PostModel) {
        posts.value = posts.value.filterNot { it.id == post.id } + post
    }

    override suspend fun deactivatePost(postId: String) {
        log.add("post:deactivate:$postId")
    }

    override suspend fun deletePost(postId: String) {
        log.add("post:delete:$postId")
        if (postId in failingIds) throw IllegalStateException("odmowa")
        if (postId in hangingIds) awaitCancellation()
        posts.value = posts.value.filterNot { it.id == postId }
    }

    override fun newPostId(): String = "nowe"
}

class AccountChatDataSource(initial: List<ChatModel> = emptyList()) : ChatDataSource {
    val chats = MutableStateFlow(initial)

    override fun getChatsForUser(userId: String): Flow<List<ChatModel>> =
        chats.map { list -> list.filter { userId in it.participants } }

    override suspend fun getChatByParticipants(psychId: String, patientId: String): ChatModel? = null
    override suspend fun saveChat(chat: ChatModel) = Unit
    override suspend fun updateChat(chat: ChatModel) = Unit
    override fun newChatId(): String = "czat"
}

class AccountNoteDataSource(private val log: EventLog, initial: List<NoteModel> = emptyList()) : NoteDataSource {
    val notes = MutableStateFlow(initial)
    val failingIds = mutableSetOf<String>()

    override fun getNotesForChat(chatId: String): Flow<List<NoteModel>> =
        notes.map { list -> list.filter { it.chatId == chatId } }

    override suspend fun saveNote(note: NoteModel) = Unit

    override suspend fun deleteNote(noteId: String) {
        log.add("note:delete:$noteId")
        if (noteId in failingIds) throw IllegalStateException("odmowa")
        notes.value = notes.value.filterNot { it.noteId == noteId }
    }
}

fun post(id: String, authorId: String = "u1") = PostModel(id = id, authorId = authorId)

fun note(id: String, chatId: String = "c1", authorId: String = "u1") =
    NoteModel(noteId = id, chatId = chatId, authorId = authorId)

fun chat(id: String, psychologistId: String, patientId: String) = ChatModel(
    chatId = id,
    psychologistId = psychologistId,
    patientId = patientId,
    participants = listOf(psychologistId, patientId)
)

fun recentLoginRequired(): Exception = RecentLoginRequiredException()

fun slot(id: String, status: String = "FREE", psychologistId: String = "u1") =
    AppointmentModel(appointmentId = id, psychologistId = psychologistId, status = status)

class AccountAppointmentDataSource(
    private val log: EventLog,
    initial: List<AppointmentModel> = emptyList()
) : AppointmentDataSource {
    val appointments = MutableStateFlow(initial)
    val failingIds = mutableSetOf<String>()
    var readFailure: Exception? = null

    override fun getFreeSlotsForPsychologist(psychologistId: String): Flow<List<AppointmentModel>> = flow {
        readFailure?.let { throw it }
        emit(appointments.value.filter { it.psychologistId == psychologistId && it.status == "FREE" })
    }

    override suspend fun deleteAppointment(appointmentId: String) {
        log.add("slot:delete:$appointmentId")
        if (appointmentId in failingIds) throw IllegalStateException("odmowa")
        appointments.value = appointments.value.filterNot { it.appointmentId == appointmentId }
    }

    override fun getAppointmentsForPsychologist(psychologistId: String): Flow<List<AppointmentModel>> = appointments
    override fun getAppointmentsForPatient(patientId: String): Flow<List<AppointmentModel>> = appointments
    override suspend fun saveAppointment(appointment: AppointmentModel) = Unit
    override suspend fun bookAppointmentAtomically(
        appointmentId: String,
        patientId: String,
        psychologistId: String,
        priceCoins: Int,
        notes: String
    ) = Unit

    override suspend fun cancelBooking(appointmentId: String, cancelledBy: String): CancelOutcome =
        throw UnsupportedOperationException()

    override suspend fun refundAbandonedBooking(appointmentId: String, patientId: String): CancelOutcome =
        throw UnsupportedOperationException()

    override suspend fun completeAppointment(appointmentId: String): CompletionOutcome = CompletionOutcome.NoCoinsMoved
    override suspend fun getAppointmentByDateTime(psychId: String, date: String, time: String): AppointmentModel? = null

    override suspend fun bookingLanded(appointmentId: String, patientId: String): Boolean = throw UnsupportedOperationException()
    override suspend fun completionLanded(appointmentId: String): CompletionOutcome? = throw UnsupportedOperationException()
    override suspend fun cancellationLanded(appointmentId: String, cancelledBy: String): CancelOutcome? = throw UnsupportedOperationException()
    override suspend fun reclaimLanded(appointmentId: String): CancelOutcome? = throw UnsupportedOperationException()
}

class AccountVerificationDataSource(
    private val log: EventLog,
    request: VerificationRequestModel? = null
) : VerificationDataSource {
    val stored = MutableStateFlow(request)
    var deleteFailure: Exception? = null

    override fun myRequest(uid: String): Flow<VerificationRequestModel?> = stored
    override fun pendingRequests(): Flow<List<VerificationRequestModel>> = flow { emit(emptyList()) }
    override suspend fun submit(request: VerificationRequestModel) = Unit
    override suspend fun decide(uid: String, approved: Boolean, note: String) = Unit

    override suspend fun deleteRequest(uid: String) {
        log.add("verification:delete")
        deleteFailure?.let { throw it }
        stored.value = null
    }
}
