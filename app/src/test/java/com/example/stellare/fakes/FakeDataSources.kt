package com.example.stellare.fakes

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.ChatModel
import com.example.stellare.data.model.CompletionModel
import com.example.stellare.data.model.CompletionOutcome
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
import com.example.stellare.data.model.VerificationStatus
import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.data.remote.AppointmentDataSource
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.remote.EscrowDataSource
import com.example.stellare.data.remote.ChatDataSource
import com.example.stellare.data.remote.FavoriteDataSource
import com.example.stellare.data.remote.MessageDataSource
import com.example.stellare.data.remote.NoteDataSource
import com.example.stellare.data.remote.PostDataSource
import com.example.stellare.data.remote.PrivateProfileDataSource
import com.example.stellare.data.remote.DocumentWrite
import com.example.stellare.data.remote.FirestoreAppointmentDataSource
import com.example.stellare.data.remote.FirestoreReviewDataSource
import com.example.stellare.data.remote.FirestoreUserDataSource
import com.example.stellare.data.remote.UserDocumentReader
import com.example.stellare.data.remote.UserDocumentWatch
import com.example.stellare.data.remote.RatingDataSource
import com.example.stellare.data.remote.RatingTransaction
import com.example.stellare.data.remote.TransactionRunner
import com.example.stellare.data.remote.ReviewDataSource
import com.example.stellare.data.remote.UserDataSource
import com.example.stellare.data.remote.VerificationDataSource
import com.example.stellare.data.remote.WalletDataSource
import com.example.stellare.data.repository.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformWhile

class FakeAuthDataSource(initialUserId: String? = null) : AuthDataSource {
    val userId = MutableStateFlow(initialUserId)

    var registerResult: Result<String?> = Result.success(null)
    var signInResult: Result<String?> = Result.success(null)
    var signInWithGoogleResult: Result<String?> = Result.success(null)
    var signOutCalled = false
        private set

    override val currentUserId: Flow<String?> = userId

    override fun getCurrentUserIdOnce(): String? = userId.value

    override suspend fun sendPasswordResetEmail(email: String) = throw UnsupportedOperationException()
    override suspend fun deleteCurrentAccount() = throw UnsupportedOperationException()

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

/**
 * Atrapa dokumentu prywatnego `users/{uid}/private/contact`. Odwzorowuje reguły: dokument tworzy
 * wyłącznie rejestracja i zawsze z saldem 0 (istniejącego nie nadpisuje, bo zapis zera nad
 * niezerowym zniszczyłby monety), numer i e-mail zapisuje się tylko do dokumentu, który istnieje
 * (zapis do brakującego kończy się NOT_FOUND), a odczyt może zawieść [readFailuresBeforeSuccess] razy.
 */
class FakePrivateProfileDataSource(initial: Map<String, PrivateProfile> = emptyMap()) : PrivateProfileDataSource {
    val profiles = MutableStateFlow(initial)
    var readFailuresBeforeSuccess = 0
    var readFailure: Throwable? = null
    var createFailure: Throwable? = null
    var subscriptionCount = 0
        private set
    val createdFor = mutableListOf<String>()

    fun balanceOf(uid: String): Int? = profiles.value[uid]?.coinsBalance

    fun setBalance(uid: String, balance: Int) {
        val current = checkNotNull(profiles.value[uid]) { "Brak dokumentu prywatnego $uid" }
        profiles.value = profiles.value + (uid to current.copy(coinsBalance = balance))
    }

    override fun getPrivateProfile(uid: String): Flow<PrivateProfile?> = flow {
        subscriptionCount++
        readFailure?.let { throw it }
        if (readFailuresBeforeSuccess > 0) {
            readFailuresBeforeSuccess--
            throw IllegalStateException("UNAVAILABLE: private profile listener failed")
        }
        emitAll(profiles.map { it[uid] })
    }

    override suspend fun createPrivateProfile(uid: String, phone: String, email: String) {
        createFailure?.let { throw it }
        createdFor += uid
        if (uid in profiles.value) return
        profiles.value = profiles.value + (uid to PrivateProfile(phone = phone, email = email, coinsBalance = 0))
    }

    override suspend fun savePhone(uid: String, phone: String) {
        val current = profiles.value[uid] ?: throw IllegalStateException("NOT_FOUND: users/$uid/private/contact")
        profiles.value = profiles.value + (uid to current.copy(phone = phone))
    }

    override suspend fun saveEmail(uid: String, email: String) {
        val current = profiles.value[uid] ?: throw IllegalStateException("NOT_FOUND: users/$uid/private/contact")
        profiles.value = profiles.value + (uid to current.copy(email = email))
    }

    override suspend fun deletePrivateProfile(uid: String) {
        profiles.value = profiles.value - uid
    }
}

/**
 * [initial] to konta zarejestrowane normalnie, więc każde dostaje dokument prywatny z saldem 0,
 * chyba że [balances] podaje inne saldo. Konto bez dokumentu prywatnego trzeba zbudować jawnie
 * (usuń wpis z [privateProfiles]).
 */
class FakeUserDataSource(
    initial: List<UserModel> = emptyList(),
    balances: Map<String, Int> = emptyMap(),
    val privateProfiles: FakePrivateProfileDataSource = FakePrivateProfileDataSource()
) : UserDataSource {
    val users = MutableStateFlow(initial)

    init {
        privateProfiles.profiles.value = privateProfiles.profiles.value +
            initial.associate { it.id to PrivateProfile(coinsBalance = balances[it.id] ?: 0) }
    }

    fun balanceOf(uid: String): Int? = privateProfiles.balanceOf(uid)

    override suspend fun deleteUser(uid: String) = throw UnsupportedOperationException()

    var getAllByRoleSubscriptionCount = 0
        private set

    val listenerFailure = MutableStateFlow<Throwable?>(null)
    val servedFromCache = MutableStateFlow(false)

    /**
     * Atrapa podaje wyłącznie te cztery fakty, które w produkcji przychodzą w migawce Firestore,
     * a klasyfikuje je **produkcyjny** [UserDocumentWatch] — inaczej atrapa liczyłaby stan sama
     * i mogłaby rozjechać się z produkcją akurat na rozróżnieniu, które jest tu dowodzone.
     *
     * Po błędzie nasłuch **kończy się i nic już nie przyśle** — tak zachowuje się Firestore po
     * odmowie reguł. Atrapa, która po awarii nadal emituje, sama się naprawia i ukrywa defekt:
     * tylko założenie nowego nasłuchu wraca do życia.
     */
    var getUserSubscriptions = 0
        private set

    override fun getUser(uid: String): Flow<CurrentUserRead> = flow {
        getUserSubscriptions++
        val watch = UserDocumentWatch()
        emitAll(
            combine(users, listenerFailure, servedFromCache) { list, failure, fromCache ->
                val document = list.firstOrNull { it.id == uid }
                watch.read(
                    user = document,
                    exists = document != null,
                    fromCache = fromCache,
                    error = failure
                ) to (failure != null)
            }.transformWhile { (read, nasluchPadl) ->
                emit(read)
                !nasluchPadl
            }
        )
    }

    var readFailure: Throwable? = null

    /**
     * `getUserOnce` idzie przez **produkcyjny** [FirestoreUserDataSource], żeby atrapa nie mogła
     * znów rozjechać się z produkcją: to rozjechanie (atrapa rzucała, produkcja połykała) czyniło
     * testy stanu `NameLookup.Unavailable` bezwartościowymi. W pamięci zostaje tylko sam odczyt
     * dokumentu, czyli to, co w produkcji robi Firestore.
     */
    private val production = FirestoreUserDataSource(
        object : UserDocumentReader {
            override suspend fun read(uid: String): UserModel? {
                readFailure?.let { throw it }
                return users.value.firstOrNull { it.id == uid }
            }
        }
    )

    override suspend fun getUserOnce(uid: String): UserModel? = production.getUserOnce(uid)

    override fun getAllByRole(role: String): Flow<List<UserModel>> =
        users.map { list -> list.filter { it.role.name == role } }
            .onStart { getAllByRoleSubscriptionCount++ }

    override fun getAllByRoleExcluding(role: String, excludeId: String): Flow<List<UserModel>> =
        users.map { list -> list.filter { it.role.name == role && it.id != excludeId } }

    var saveFailure: Throwable? = null

    override suspend fun saveUser(user: UserModel) {
        saveFailure?.let { throw it }
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
    var subscriptionCount = 0
        private set

    override fun getNotesForChat(chatId: String): Flow<List<NoteModel>> =
        notes.map { list -> list.filter { it.chatId == chatId } }
            .onStart { subscriptionCount++ }

    override suspend fun saveNote(note: NoteModel) {
        notes.value = notes.value.filterNot { it.noteId == note.noteId } + note
    }

    override suspend fun deleteNote(noteId: String) {
        notes.value = notes.value.filterNot { it.noteId == noteId }
    }
}

/**
 * Atrapa źródła terminów. [users] i [wallet] są wymagane, nie opcjonalne — opcjonalna
 * zależność z domyślnym `null` pozwalała pomijać sprawdzanie salda i zapis do księgi,
 * więc test rezerwacji wyglądał na sprawdzający przepływ monet, a nie sprawdzał go wcale.
 * Ten sam [users] trzeba podać do [fakeUserRepository].
 *
 * Rezerwacja, odwołanie, zakończenie i zwrot po oknie przechodzą przez produkcyjne
 * [FirestoreAppointmentDataSource] z [FakeSettlementRunner], który odwzorowuje reguły
 * Firestore dla całej partii (wizyta, depozyt, saldo z deklaracją, księga). Test sprawdza więc
 * produkcyjny kod transakcji, a partia, której reguła by nie przepuściła, kończy się odmową.
 */
class FakeAppointmentDataSource(
    initial: List<AppointmentModel> = emptyList(),
    private val users: FakeUserDataSource,
    private val wallet: FakeWalletDataSource,
    clock: () -> Long = System::currentTimeMillis
) : AppointmentDataSource {
    val appointments = MutableStateFlow(initial)
    val world = FakeSettlementWorld(appointments, users, wallet, clock)
    val transactions = FakeSettlementRunner(world)
    val escrows: MutableStateFlow<Map<String, EscrowModel>> get() = world.escrows
    val completions: MutableStateFlow<Map<String, CompletionModel>> get() = world.completions
    private val production = FirestoreAppointmentDataSource(clock, transactions)

    /**
     * Skrót dla fixtur zaczynających od wizyty już zarezerwowanej: zakłada depozyt HELD bez
     * pobierania monet, bo salda w takiej fixturze są podane PO zapłacie. Świat z tym skrótem
     * ma więc w sumie o kwotę depozytu więcej niż suma sald, więc testy niezmiennika sumy
     * (EscrowFlowTest, EscrowMultiDocumentTest) go nie używają i rezerwują przez produkcyjne
     * bookAppointmentAtomically. Używają go tylko testy widoku (CancelRefundTest, TransferStateTest),
     * które nie asercjonują sumy.
     */
    fun seedDeposit(appointmentId: String) {
        val appointment = checkNotNull(world.appointment(appointmentId)) { "Brak wizyty $appointmentId" }
        escrows.value = escrows.value + (
            appointmentId to EscrowModel(appointmentId, checkNotNull(appointment.patientId), appointment.psychologistId, appointment.priceCoins)
            )
    }

    override fun getAppointmentsForPsychologist(psychologistId: String): Flow<List<AppointmentModel>> =
        appointments.map { list -> list.filter { it.psychologistId == psychologistId } }

    override fun getAppointmentsForPatient(patientId: String): Flow<List<AppointmentModel>> =
        appointments.map { list -> list.filter { it.patientId == patientId } }

    override fun getFreeSlotsForPsychologist(psychologistId: String): Flow<List<AppointmentModel>> =
        appointments.map { list ->
            list.filter { it.psychologistId == psychologistId && it.status == "FREE" }
        }

    var signedInUserId: String? = null
    val slotDenials = mutableListOf<String>()

    override suspend fun saveAppointment(appointment: AppointmentModel) {
        try {
            transactions.authorizeSlotCreation(appointment, signedInUserId)
        } catch (e: IllegalStateException) {
            slotDenials += e.message.orEmpty()
            throw e
        }
        appointments.value = appointments.value.filterNot { it.appointmentId == appointment.appointmentId } + appointment
    }

    override suspend fun bookAppointmentAtomically(
        appointmentId: String,
        patientId: String,
        psychologistId: String,
        priceCoins: Int,
        notes: String
    ) {
        transactions.actor = patientId
        production.bookAppointmentAtomically(appointmentId, patientId, psychologistId, priceCoins, notes)
    }

    override suspend fun cancelBooking(appointmentId: String, cancelledBy: String): CancelOutcome {
        transactions.actor = cancelledBy
        return production.cancelBooking(appointmentId, cancelledBy)
    }

    override suspend fun refundAbandonedBooking(appointmentId: String, patientId: String): CancelOutcome {
        transactions.actor = patientId
        return production.refundAbandonedBooking(appointmentId, patientId)
    }

    override suspend fun completeAppointment(appointmentId: String): CompletionOutcome {
        transactions.actor = world.appointment(appointmentId)?.psychologistId.orEmpty()
        return production.completeAppointment(appointmentId)
    }

    override suspend fun deleteAppointment(appointmentId: String) {
        transactions.authorizeSlotDeletion(appointmentId, signedInUserId)
        appointments.value = appointments.value.filterNot { it.appointmentId == appointmentId }
    }

    override suspend fun getAppointmentByDateTime(psychId: String, date: String, time: String): AppointmentModel? =
        appointments.value.firstOrNull {
            it.psychologistId == psychId && it.date == date && it.time == time
        }

    override suspend fun bookingLanded(appointmentId: String, patientId: String): Boolean {
        transactions.actor = patientId
        return production.bookingLanded(appointmentId, patientId)
    }

    override suspend fun completionLanded(appointmentId: String): CompletionOutcome? {
        transactions.actor = world.appointment(appointmentId)?.psychologistId.orEmpty()
        return production.completionLanded(appointmentId)
    }

    override suspend fun cancellationLanded(appointmentId: String, cancelledBy: String): CancelOutcome? {
        transactions.actor = cancelledBy
        return production.cancellationLanded(appointmentId, cancelledBy)
    }

    override suspend fun reclaimLanded(appointmentId: String): CancelOutcome? =
        production.reclaimLanded(appointmentId)
}

class FakeEscrowDataSource(private val appointments: FakeAppointmentDataSource) : EscrowDataSource {
    override fun observeEscrow(appointmentId: String): Flow<EscrowModel?> =
        appointments.escrows.map { it[appointmentId] }
}

class FakeWalletDataSource(private val users: FakeUserDataSource? = null) : WalletDataSource {
    val transactions = MutableStateFlow<List<WalletTransactionModel>>(emptyList())
    var topUpFailure: Throwable? = null

    var subscriptionCount = 0
        private set
    var activeSubscriptions = 0
        private set

    var listenerFailure: Throwable? = null

    /** Nasłuch, który został założony, ale serwer jeszcze nie odpowiedział: ani danych, ani błędu. */
    var listenerGate: CompletableDeferred<Unit>? = null

    override fun getTransactionsForUser(userId: String): Flow<List<WalletTransactionModel>> =
        transactions.map { list -> list.filter { it.userId == userId } }
            .transform { list ->
                listenerGate?.await()
                listenerFailure?.let { throw it }
                emit(list)
            }
            .onStart {
                subscriptionCount++
                activeSubscriptions++
            }
            .onCompletion { activeSubscriptions-- }

    override suspend fun saveTransaction(transaction: WalletTransactionModel) {
        transactions.value = transactions.value.filterNot { it.transactionId == transaction.transactionId } + transaction
    }

    var topUpResponseLost: Throwable? = null
    var topUpLandedFailure: Throwable? = null
    var topUpWrites = 0
        private set

    override suspend fun topUp(userId: String, amount: Int, title: String, timestamp: Long, entryId: String) {
        val store = checkNotNull(users) { "Atrapa portfela bez powiazanych uzytkownikow nie obsluguje doladowania." }
        topUpWrites++
        topUpFailure?.let { throw it }
        check(store.privateProfiles.profiles.value.containsKey(userId)) { "NOT_FOUND: users/$userId/private/contact" }
        check(amount in 1..TOP_UP_LIMIT) { "PERMISSION_DENIED: doladowanie ponad limit $TOP_UP_LIMIT" }
        check(transactions.value.none { it.transactionId == entryId }) {
            "PERMISSION_DENIED: wpis ksiegi $entryId juz istnieje, ksiega jest niezmienna"
        }
        val entry = WalletTransactionModel(
            transactionId = entryId,
            userId = userId,
            amount = amount,
            type = WalletTransactionType.TOPUP,
            title = title,
            timestamp = timestamp
        )
        store.privateProfiles.setBalance(userId, checkNotNull(store.balanceOf(userId)) + amount)
        transactions.value = transactions.value + entry
        topUpResponseLost?.let { failure ->
            topUpResponseLost = null
            throw failure
        }
    }

    var topUpLandedCalls = 0
        private set

    override suspend fun topUpLanded(userId: String, entryId: String): Boolean {
        topUpLandedCalls++
        topUpLandedFailure?.let { throw it }
        return transactions.value.any { it.transactionId == entryId && it.userId == userId }
    }

    private companion object {
        const val TOP_UP_LIMIT = 1000
    }
}

class FakeRatingDataSource : RatingDataSource {
    val ratings = MutableStateFlow<Map<String, RatingModel>>(emptyMap())

    var getRatingsSubscriptionCount = 0
        private set

    var failuresBeforeSuccess = 0

    var readFailure: Throwable? = null

    override fun getRatings(): Flow<Map<String, RatingModel>> = flow {
        getRatingsSubscriptionCount++
        readFailure?.let { throw it }
        if (failuresBeforeSuccess > 0) {
            failuresBeforeSuccess--
            throw IllegalStateException("UNAVAILABLE: ratings listener failed")
        }
        emitAll(ratings)
    }

    override fun getRating(psychologistId: String): Flow<RatingModel> =
        ratings.map { it[psychologistId] ?: RatingModel() }
}

/**
 * Atrapa źródła recenzji odwzorowuje reguły `reviews` i `ratings` z firestore.rules:
 * recenzja powstaje tylko raz, tylko po odbytej wizycie, pod identyfikatorem
 * `<psycholog>_<recenzent>`, z oceną 1-5 i nie dla samego siebie, a agregat musi być
 * dokładnie poprzednim agregatem powiększonym o tę ocenę (create: sum == ocena, count == 1),
 * zapisanym w tym samym zatwierdzeniu co recenzja.
 * Zapis przechodzi przez produkcyjne [FirestoreReviewDataSource] z [FakeTransactionRunner],
 * który jak transakcja Firestore wykrywa zmianę odczytanej wartości przed zatwierdzeniem
 * i ponawia blok, więc test sprawdza produkcyjny kod odczytu i zapisu, a nie jego kopię.
 */
class FakeReviewDataSource(
    private val ratingSource: FakeRatingDataSource
) : ReviewDataSource {
    val reviews = MutableStateFlow<List<ReviewModel>>(emptyList())
    val completions = mutableSetOf<String>()
    var lastRatingUpdate: Pair<Int, Int>? = null
        private set

    val transactions = FakeTransactionRunner(ratingSource, ::commit)
    private val production = FirestoreReviewDataSource(transactions)

    override suspend fun hasCompletedSession(psychologistId: String, patientId: String): Boolean =
        "${psychologistId}_$patientId" in completions

    override fun getReviewsForPsychologist(psychologistId: String): Flow<List<ReviewModel>> =
        reviews.map { list -> list.filter { it.psychologistId == psychologistId } }

    override suspend fun saveReviewWithRating(review: ReviewModel, psychologistId: String) {
        production.saveReviewWithRating(review, psychologistId)
    }

    private fun commit(writes: List<DocumentWrite>) {
        val reviewWrite = writes.singleOrNull { it.collection == "reviews" }
        val ratingWrite = writes.singleOrNull { it.collection == "ratings" }
        if (reviewWrite == null || ratingWrite == null) {
            throw IllegalStateException("PERMISSION_DENIED: aggregate and review must be written in one commit")
        }
        val review = reviewWrite.data as ReviewModel
        val psychologistId = ratingWrite.id
        @Suppress("UNCHECKED_CAST")
        val aggregate = ratingWrite.data as Map<String, Int>
        val newSum = aggregate.getValue("sum")
        val newCount = aggregate.getValue("count")
        val expectedId = "${psychologistId}_${review.reviewerId}"
        val current = ratingSource.ratings.value[psychologistId] ?: RatingModel()
        val denial = when {
            review.reviewId != expectedId || reviewWrite.id != expectedId || review.psychologistId != psychologistId ->
                "review id must be <psychologist>_<reviewer>"
            review.reviewerId == psychologistId -> "psychologist cannot rate self"
            review.rating !in 1..5 -> "rating must be 1-5"
            reviews.value.any { it.reviewId == review.reviewId } -> "review already exists"
            expectedId !in completions -> "no completed session"
            newCount != current.count + 1 -> "count must grow by exactly one"
            newSum != current.sum + review.rating -> "sum must grow by exactly the rating"
            else -> null
        }
        if (denial != null) throw IllegalStateException("PERMISSION_DENIED: $denial")
        reviews.value = reviews.value + review
        lastRatingUpdate = newSum to newCount
        ratingSource.ratings.value = ratingSource.ratings.value + (psychologistId to RatingModel(newSum, newCount))
    }
}

class FakeTransactionRunner(
    private val ratingSource: FakeRatingDataSource,
    private val commit: (List<DocumentWrite>) -> Unit
) : TransactionRunner {
    val staleReads = ArrayDeque<RatingModel>()
    var beforeFirstCommit: (suspend () -> Unit)? = null
    var attempts = 0
        private set

    override suspend fun <T> run(block: (RatingTransaction) -> T): T {
        repeat(MAX_ATTEMPTS) {
            attempts++
            val reads = mutableMapOf<String, RatingModel>()
            val staged = mutableListOf<List<DocumentWrite>>()
            val transaction = object : RatingTransaction {
                override fun readRating(psychologistId: String): RatingModel {
                    val value = staleReads.removeFirstOrNull()
                        ?: (ratingSource.ratings.value[psychologistId] ?: RatingModel())
                    reads[psychologistId] = value
                    return value
                }

                override fun write(writes: List<DocumentWrite>) {
                    staged += writes
                }
            }
            val result = block(transaction)
            beforeFirstCommit?.let { hook ->
                beforeFirstCommit = null
                hook()
            }
            val conflict = reads.any { (id, seen) -> (ratingSource.ratings.value[id] ?: RatingModel()) != seen }
            if (!conflict) {
                commit(staged.flatten())
                return result
            }
        }
        throw IllegalStateException("ABORTED: too much contention")
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

/**
 * Atrapa źródła zgłoszeń weryfikacyjnych. [decide] MUSI być tak samo rygorystyczna jak
 * [FirestoreVerificationDataSource][com.example.stellare.data.remote.FirestoreVerificationDataSource]:
 * zatwierdzenie ustawia status zgłoszenia ORAZ odznakę `isVerified` u użytkownika w jednym
 * logicznym kroku (tam — jeden `batch()`), odrzucenie ustawia tylko status i notatkę, nie
 * rusza odznaki. Rozjazd atrapy ze źródłem w tym projekcie ugryzł już pięć razy, zawsze
 * objawiając się zielonym testem, który niczego nie sprawdzał. [userDataSource] jest
 * wymagany, nie opcjonalny — opcjonalna zależność z domyślnym `null` pozwoliłaby [decide]
 * po cichu pomijać ustawianie odznaki.
 */
class FakeVerificationDataSource(
    private val userDataSource: FakeUserDataSource
) : VerificationDataSource {
    val requests = MutableStateFlow<List<VerificationRequestModel>>(emptyList())
    private var reviewClock = 0L

    override fun myRequest(uid: String): Flow<VerificationRequestModel?> =
        requests.map { list -> list.firstOrNull { it.uid == uid } }

    override fun pendingRequests(): Flow<List<VerificationRequestModel>> =
        requests.map { list -> list.filter { it.status == VerificationStatus.PENDING } }

    override suspend fun submit(request: VerificationRequestModel) {
        require(request.uid.isNotEmpty()) { "Identyfikator zgłaszającego nie może być pusty." }
        requests.value = requests.value.filterNot { it.uid == request.uid } + request
    }

    override suspend fun deleteRequest(uid: String) {
        requests.value = requests.value.filterNot { it.uid == uid }
    }

    var decideFailure: Throwable? = null

    override suspend fun decide(uid: String, approved: Boolean, note: String) {
        decideFailure?.let { throw it }
        val status = if (approved) VerificationStatus.APPROVED else VerificationStatus.REJECTED
        requests.value = requests.value.map {
            if (it.uid == uid) {
                it.copy(
                    status = status,
                    reviewedBy = "admin",
                    reviewedAt = ++reviewClock,
                    reviewNote = note
                )
            } else {
                it
            }
        }
        if (approved) {
            val user = userDataSource.users.value.firstOrNull { it.id == uid }
            if (user != null) {
                userDataSource.saveUser(user.copy(isVerified = true))
            }
        }
    }
}

/**
 * Atrapa źródła ogłoszeń. Musi być tak samo rygorystyczna jak
 * [FirestorePostDataSource][com.example.stellare.data.remote.FirestorePostDataSource]:
 * [getActivePosts] filtruje po `isActive`, a [deactivatePost] ustawia flagę
 * zamiast usuwać dokument. Rozjazd atrapy ze źródłem w tym projekcie ugryzł
 * już pięć razy, zawsze objawiając się zielonym testem, który niczego nie sprawdzał.
 */
class FakePostDataSource : PostDataSource {
    val posts = MutableStateFlow<List<PostModel>>(emptyList())
    private var licznik = 0

    override suspend fun deletePost(postId: String) = throw UnsupportedOperationException()

    override fun getActivePosts(type: String): Flow<List<PostModel>> =
        posts.map { list -> list.filter { it.isActive && it.type.name == type } }

    override fun getPostsByAuthor(authorId: String): Flow<List<PostModel>> =
        posts.map { list -> list.filter { it.authorId == authorId } }

    override suspend fun savePost(post: PostModel) {
        require(post.id.isNotEmpty()) { "Identyfikator ogłoszenia nie może być pusty." }
        posts.value = posts.value.filterNot { it.id == post.id } + post
    }

    override suspend fun deactivatePost(postId: String) {
        posts.value = posts.value.map {
            if (it.id == postId) it.copy(isActive = false) else it
        }
    }

    override fun newPostId(): String = "post-${++licznik}"
}

/**
 * Buduje [UserRepository] oparte wyłącznie na atrapach, z zalogowanym użytkownikiem [uid]
 * mającym saldo [coinsBalance] monet (domyślnie 500). Testy sprawdzające saldo lub księgę
 * (np. rezerwację bez pokrycia) powinny przekazać własny, współdzielony [userDataSource] —
 * ten sam obiekt trzeba wtedy podać jako `users` do [FakeAppointmentDataSource].
 */
fun fakeUserRepository(
    uid: String,
    coinsBalance: Int = 500,
    userDataSource: FakeUserDataSource = FakeUserDataSource()
): UserRepository {
    userDataSource.users.value = userDataSource.users.value.filterNot { it.id == uid } + UserModel(id = uid)
    userDataSource.privateProfiles.profiles.value = userDataSource.privateProfiles.profiles.value +
        (uid to PrivateProfile(coinsBalance = coinsBalance))
    return UserRepository(
        FakeAuthDataSource(uid),
        userDataSource,
        FakeFavoriteDataSource(),
        FakeRatingDataSource(),
        userDataSource.privateProfiles
    )
}

fun privateProfilesOf(users: UserDataSource): PrivateProfileDataSource =
    (users as? FakeUserDataSource)?.privateProfiles ?: FakePrivateProfileDataSource()
