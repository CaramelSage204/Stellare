package com.example.stellare.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.credentials.Credential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.ChatModel
import com.example.stellare.data.model.MessageModel
import com.example.stellare.data.model.NoteModel
import com.example.stellare.data.model.ReviewModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.remote.FirebaseAuthDataSource
import com.example.stellare.data.remote.FirestoreAppointmentDataSource
import com.example.stellare.data.remote.FirestoreChatDataSource
import com.example.stellare.data.remote.FirestoreFavoriteDataSource
import com.example.stellare.data.remote.FirestoreMessageDataSource
import com.example.stellare.data.remote.FirestoreNoteDataSource
import com.example.stellare.data.remote.FirestoreReviewDataSource
import com.example.stellare.data.remote.FirestoreUserDataSource
import com.example.stellare.data.remote.FirestoreWalletTransactionDataSource
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.data.repository.ChatRepository
import com.example.stellare.data.repository.ReviewRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.WalletRepository
import com.example.stellare.ui.navigation.Screen
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
class StellareViewModel(application: Application) : AndroidViewModel(application) {
    private val userRepository = UserRepository(
        FirebaseAuthDataSource(),
        FirestoreUserDataSource(),
        FirestoreFavoriteDataSource()
    )
    private val chatRepository = ChatRepository(
        FirestoreChatDataSource(),
        FirestoreMessageDataSource(),
        FirestoreNoteDataSource()
    )
    private val reviewRepository = ReviewRepository(FirestoreReviewDataSource())
    private val appointmentRepository = AppointmentRepository(FirestoreAppointmentDataSource())
    private val walletRepository = WalletRepository(FirestoreWalletTransactionDataSource())

    // Current Navigation State with a basic backstack
    private val _navigationStack = MutableStateFlow<List<Screen>>(listOf(Screen.Onboarding))
    val currentScreen: StateFlow<Screen> = _navigationStack
        .map { it.lastOrNull() ?: Screen.Onboarding }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Screen.Onboarding)

    val canNavigateBack: StateFlow<Boolean> = _navigationStack
        .map { it.size > 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // UI States
    val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _showTutorialPrompt = MutableStateFlow(false)
    val showTutorialPrompt: StateFlow<Boolean> = _showTutorialPrompt.asStateFlow()

    fun setShowTutorialPrompt(show: Boolean) {
        _showTutorialPrompt.value = show
    }

    // Search filters state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Professional filters
    private val _filterAgeMin = MutableStateFlow(18)
    val filterAgeMin = _filterAgeMin.asStateFlow()

    private val _filterAgeMax = MutableStateFlow(100)
    val filterAgeMax = _filterAgeMax.asStateFlow()

    private val _filterGender = MutableStateFlow("Wszystkie") // "Wszystkie", "Kobieta", "Mężczyzna"
    val filterGender = _filterGender.asStateFlow()

    private val _filterSpec = MutableStateFlow("Wszystkie") // "Wszystkie", "Rodzina", "Ogólny", "Życie intymne", "Praca", etc.
    val filterSpec = _filterSpec.asStateFlow()

    private val _filterVerifiedOnly = MutableStateFlow(false)
    val filterVerifiedOnly = _filterVerifiedOnly.asStateFlow()

    private val _filterPriceMax = MutableStateFlow(400.0)
    val filterPriceMax = _filterPriceMax.asStateFlow()

    private val _filterMinRating = MutableStateFlow(0.0)
    val filterMinRating = _filterMinRating.asStateFlow()

    // List of active users of specific roles, updated by database + filters
    val filteredPsychologists: StateFlow<List<UserModel>> = combine(
        userRepository.getUsersOfRoleFlow(UserRole.PSYCHOLOGIST).combine(userRepository.getUsersOfRoleFlow(
            UserRole.PSYCHOLOGY_STUDENT)) { p, s -> p + s },
        _searchQuery,
        _filterAgeMin,
        _filterAgeMax,
        _filterGender,
        _filterSpec,
        _filterVerifiedOnly,
        _filterPriceMax,
        _filterMinRating
    ) { args: Array<Any> ->
        val list = args[0] as List<UserModel>
        val query = args[1] as String
        val ageMin = args[2] as Int
        val ageMax = args[3] as Int
        val gender = args[4] as String
        val spec = args[5] as String
        val verifiedOnly = args[6] as Boolean
        val priceMax = args[7] as Double
        val minRating = args[8] as Double

        list.filter { user ->
            val matchesQuery = query.isEmpty() || 
                    "${user.firstName} ${user.lastName}".contains(query, ignoreCase = true) ||
                    user.specializations.contains(query, ignoreCase = true)
            val matchesAge = user.age in ageMin..ageMax
            val matchesGender = gender == "Wszystkie" || user.gender.equals(gender, ignoreCase = true)
            val matchesSpec = spec == "Wszystkie" || user.specializations.contains(spec, ignoreCase = true)
            val matchesVerified = !verifiedOnly || user.isVerified
            val matchesPrice = user.pricePerSession <= priceMax
            val matchesRating = user.rating >= minRating

            matchesQuery && matchesAge && matchesGender && matchesSpec && matchesVerified && matchesPrice && matchesRating
        }.sortedByDescending { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getUserById(userId: String): UserModel? = userRepository.getUserById(userId)

    val filteredPatients: StateFlow<List<UserModel>> = combine(
        userRepository.getUsersOfRoleFlow(UserRole.PATIENT),
        _searchQuery
    ) { list, query ->
        list.filter { user ->
            val matchesQuery = query.isEmpty() || 
                    "${user.firstName} ${user.lastName}".contains(query, ignoreCase = true) ||
                    user.bio.contains(query, ignoreCase = true)
            matchesQuery
        }.sortedByDescending { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Chats
    val activeChats: StateFlow<List<ChatModel>> = currentUser
        .flatMapLatest { user ->
            if (user != null) {
                chatRepository.getChatsForUserFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Favorites
    val favoritePsychologistsIds: StateFlow<Set<String>> = currentUser
        .flatMapLatest { user ->
            if (user != null) {
                userRepository.getFavoritesForUserFlow(user.id).map { list -> list.map { it.psychologistId }.toSet() }
            } else {
                flowOf(emptySet())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val favoritePsychologists: StateFlow<List<UserModel>> = combine(
        currentUser,
        userRepository.getUsersOfRoleFlow(UserRole.PSYCHOLOGIST).combine(userRepository.getUsersOfRoleFlow(
            UserRole.PSYCHOLOGY_STUDENT)) { p, s -> p + s },
        userRepository.getUsersOfRoleFlow(UserRole.PATIENT),
        favoritePsychologistsIds
    ) { user, allPsychs, allPatients, favIds ->
        val isPsych = user?.role == UserRole.PSYCHOLOGIST || user?.role == UserRole.PSYCHOLOGY_STUDENT
        val targetList = if (isPsych) allPatients else allPsychs
        targetList.filter { targetUser -> favIds.contains(targetUser.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFavorite(psychologistId: String) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val currentFavs = favoritePsychologistsIds.value
            if (currentFavs.contains(psychologistId)) {
                userRepository.deleteFavorite(user.id, psychologistId)
            } else {
                userRepository.insertFavorite(user.id, psychologistId)
            }
        }
    }

    // Map helper to resolve other participants in chats
    private val _chatParticipants = MutableStateFlow<Map<String, UserModel>>(emptyMap())
    val chatParticipants = _chatParticipants.asStateFlow()

    init {
        viewModelScope.launch {
            loadChatParticipants()
        }
    }

    private suspend fun loadChatParticipants() {
        // Collect users and cache them for chat views
        userRepository.getUsersOfRoleFlow(UserRole.PSYCHOLOGIST).combine(userRepository.getUsersOfRoleFlow(
            UserRole.PATIENT)) { psychs, patients ->
            psychs + patients
        }.collect { allUsers ->
            _chatParticipants.value = allUsers.associateBy { it.id }
        }
    }

    // Navigation Drawer Toggle state
    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen = _isDrawerOpen.asStateFlow()

    fun toggleDrawer(open: Boolean) {
        _isDrawerOpen.value = open
    }

    fun navigateTo(screen: Screen) {
        val current = _navigationStack.value.toMutableList()
        current.add(screen)
        _navigationStack.value = current
        _isDrawerOpen.value = false
    }

    fun navigateBack() {
        val current = _navigationStack.value.toMutableList()
        if (current.size > 1) {
            current.removeAt(current.size - 1)
            _navigationStack.value = current
        }
    }

    fun logout() {
        viewModelScope.launch {
            FirebaseAuthDataSource().signOut()
            _navigationStack.value = listOf(Screen.Onboarding)
        }
    }

    fun buildGoogleSignInRequest(context: Context): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId("818324884549-c7i2ohrnv3ksb3f9u2jhtqd4spq97lim.apps.googleusercontent.com") // from Firebase Console
            .build()
        return GetCredentialRequest(
            credentialOptions = listOf(googleIdOption)
        )
    }

    fun signInWithGoogle(credential: Credential) {
        viewModelScope.launch {
            _authError.value = null
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                Firebase.auth.signInWithCredential(firebaseCredential).await()
                navigateTo(Screen.MainDashboard)
            } catch (e: Exception) {
                Log.e("Auth", "Google sign-in failed", e)
                _authError.value = "Błąd logowania Google: ${e.localizedMessage}"
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFilters(
        ageMin: Int,
        ageMax: Int,
        gender: String,
        spec: String,
        verifiedOnly: Boolean,
        priceMax: Double,
        minRating: Double
    ) {
        _filterAgeMin.value = ageMin
        _filterAgeMax.value = ageMax
        _filterGender.value = gender
        _filterSpec.value = spec
        _filterVerifiedOnly.value = verifiedOnly
        _filterPriceMax.value = priceMax
        _filterMinRating.value = minRating
    }

    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _searchQuery.value = ""
            _filterAgeMin.value = 18
            _filterAgeMax.value = 100
            _filterGender.value = "Wszystkie"
            _filterSpec.value = "Wszystkie"
            _filterVerifiedOnly.value = false
            _filterPriceMax.value = 400.0
            _filterMinRating.value = 0.0
            delay(800)
            _isRefreshing.value = false
        }
    }

    fun registerUser(
        firstName: String,
        lastName: String,
        age: Int,
        gender: String,
        phone: String,
        email: String,
        password: String,
        role: UserRole,
        isVerified: Boolean = false,
        qualifications: String = "",
        specializations: String = "",
        pricePerSession: Double = 0.0,
        bio: String = "",
        customPrices: String = ""
    ) {
        viewModelScope.launch {
            _authError.value = null
            try {
                val firebaseUser = FirebaseAuthDataSource().registerWithEmail(email, password)
                val uid = firebaseUser?.uid ?: throw Exception("Nie udało się utworzyć konta.")
                val user = UserModel(
                    id = uid,
                    firstName = firstName,
                    lastName = lastName,
                    age = age,
                    gender = gender,
                    phone = phone,
                    email = email,
                    role = role,
                    isVerified = isVerified,
                    qualifications = qualifications,
                    specializations = specializations,
                    pricePerSession = pricePerSession,
                    rating = if (role == UserRole.PSYCHOLOGIST) 5.0 else 0.0,
                    ratingCount = if (role == UserRole.PSYCHOLOGIST) 1 else 0,
                    bio = bio,
                    isCurrentUser = true,
                    customPrices = customPrices
                )
                userRepository.insertUser(user)
                _showTutorialPrompt.value = true
                navigateTo(Screen.MainDashboard)
            } catch (e: Exception) {
                Log.e("Auth", "Registration failed", e)
                _authError.value = "Błąd rejestracji: ${e.localizedMessage}"
            }
        }
    }

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun clearAuthError() {
        _authError.value = null
    }

    fun loginUser(email: String, password: String) {
        viewModelScope.launch {
            _authError.value = null
            try {
                val firebaseUser = FirebaseAuthDataSource().signInWithEmail(email, password)
                if (firebaseUser != null) {
                    val user = userRepository.getUserById(firebaseUser.uid)
                    if (user != null) {
                        navigateTo(Screen.MainDashboard)
                    } else {
                        _authError.value = "Nie znaleziono danych użytkownika w bazie."
                    }
                } else {
                    _authError.value = "Błędny e-mail lub hasło."
                }
            } catch (e: Exception) {
                Log.e("Auth", "Login failed", e)
                _authError.value = "Błąd logowania: ${e.localizedMessage}"
            }
        }
    }

    fun publishOffer(
        firstName: String,
        lastName: String,
        age: Int,
        gender: String,
        spec: String,
        price: Double,
        bio: String,
        qual: String = ""
    ) {
        viewModelScope.launch {
            val curr = currentUser.value ?: return@launch
            val updated = curr.copy(
                firstName = firstName,
                lastName = lastName,
                age = age,
                gender = gender,
                isVerified = curr.role == UserRole.PSYCHOLOGIST && qual.isNotEmpty(),
                qualifications = qual,
                specializations = spec,
                pricePerSession = price,
                bio = bio
            )
            userRepository.updateUser(updated)
            _searchQuery.value = ""
        }
    }
    fun updateProfile(
        firstName: String,
        lastName: String,
        age: Int,
        gender: String,
        bio: String,
        specializations: String,
        pricePerSession: Double,
        customPrices: String = "",
        profileImageUri: String? = null
    ) {
        viewModelScope.launch {
            val current = currentUser.value ?: return@launch
            val updated = current.copy(
                firstName = firstName,
                lastName = lastName,
                age = age,
                gender = gender,
                bio = bio,
                specializations = specializations,
                pricePerSession = pricePerSession,
                customPrices = customPrices,
                profileImageUri = profileImageUri ?: current.profileImageUri
            )
            userRepository.updateUser(updated)
            navigateBack()
        }
    }

    fun completePsychologistVerification(qual: String) {
        viewModelScope.launch {
            val current = currentUser.value ?: return@launch
            val updated = current.copy(
                isVerified = true,
                qualifications = qual
            )
            userRepository.updateUser(updated)
        }
    }

    // Get Chat messages
    fun getMessagesForChat(chatId: String): Flow<List<MessageModel>> = chatRepository.getMessagesForChatFlow(chatId)

    // Get Notes (Zametki)
    fun getNotesForChat(chatId: String): Flow<List<NoteModel>> = chatRepository.getNotesForChatFlow(chatId)

    fun sendChatMessage(chatId: String, text: String) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            val msg = MessageModel(
                chatId = chatId,
                senderId = user.id,
                text = text,
                timestamp = System.currentTimeMillis()
            )
            chatRepository.insertMessage(msg)
            
            val chatList = activeChats.value
            val chat = chatList.find { it.chatId == chatId }
            if (chat != null) {
                chatRepository.updateChat(chat.copy(lastMessage = text, lastMessageTime = System.currentTimeMillis()))
            }
        }
    }

    fun saveClientNote(chatId: String, title: String, content: String) {
        viewModelScope.launch {
            val note = NoteModel(chatId = chatId, title = title, content = content)
            chatRepository.insertNote(note)
        }
    }

    fun deleteClientNote(noteId: String) {
        viewModelScope.launch {
            chatRepository.deleteNote(noteId)
        }
    }

    // Get reviews flow for detail view
    fun getReviewsForPsychologist(psychologistId: String): Flow<List<ReviewModel>> = reviewRepository.getReviewsForPsychologistFlow(psychologistId)

    fun addReview(psychologistId: String, rating: Int, comment: String) {
        viewModelScope.launch {
            val current = currentUser.value
            val reviewerName = if (current != null) "${current.firstName} ${current.lastName}" else "Anonim"
            reviewRepository.insertReview(
                ReviewModel(
                    psychologistId = psychologistId,
                    reviewerName = reviewerName,
                    rating = rating,
                    comment = comment
                )
            )

            // Recalculate psych rating
            val p = userRepository.getUserById(psychologistId) ?: return@launch
            val newCount = p.ratingCount + 1
            val newRating = ((p.rating * p.ratingCount) + rating) / newCount
            userRepository.updateUser(p.copy(rating = newRating, ratingCount = newCount))
        }
    }

    // --- APPOINTMENTS / CALENDAR STATE & ACTIONS ---
    val currentPsychologistAppointments: StateFlow<List<AppointmentModel>> = currentUser
        .flatMapLatest { user ->
            if (user != null && (user.role == UserRole.PSYCHOLOGIST || user.role == UserRole.PSYCHOLOGY_STUDENT)) {
                appointmentRepository.getAppointmentsForPsychologistFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPatientAppointments: StateFlow<List<AppointmentModel>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.role == UserRole.PATIENT) {
                appointmentRepository.getAppointmentsForPatientFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val walletTransactions: StateFlow<List<WalletTransactionModel>> = currentUser
        .flatMapLatest { user ->
            if (user != null) {
                walletRepository.getWalletTransactionsFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun topUpWallet(amount: Int) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val transaction = WalletTransactionModel(
                userId = user.id,
                amount = amount,
                title = "Doładowanie portfela"
            )
            walletRepository.insertWalletTransaction(transaction)
            userRepository.updateUser(user.copy(coinsBalance = user.coinsBalance + amount))
        }
    }

    fun spendCoins(amount: Int, title: String): Boolean {
        val user = currentUser.value ?: return false
        if (user.coinsBalance < amount) return false
        viewModelScope.launch {
            val transaction = WalletTransactionModel(
                userId = user.id,
                amount = -amount,
                title = title
            )
            walletRepository.insertWalletTransaction(transaction)
            userRepository.updateUser(user.copy(coinsBalance = user.coinsBalance - amount))
        }
        return true
    }

    fun getFreeSlotsForPsychologist(psychId: String): Flow<List<AppointmentModel>> {
        return appointmentRepository.getFreeSlotsForPsychologistFlow(psychId)
    }

    fun addFreeSlot(date: String, time: String) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            appointmentRepository.insertAppointment(
                AppointmentModel(
                    psychologistId = user.id,
                    date = date,
                    time = time,
                    status = "FREE"
                )
            )
        }
    }

    fun bookAppointment(appointmentId: String, notes: String = "", costInCoins: Int = 40): Boolean {
        val user = currentUser.value ?: return false
        if (user.coinsBalance < costInCoins) return false
        viewModelScope.launch {
            // Deduct coins
            val transaction = WalletTransactionModel(
                userId = user.id,
                amount = -costInCoins,
                title = "Opłata za wizytę ($notes)"
            )
            walletRepository.insertWalletTransaction(transaction)
            userRepository.updateUser(user.copy(coinsBalance = user.coinsBalance - costInCoins))

            // Book slot
            appointmentRepository.bookAppointment(appointmentId, user.id, notes)
        }
        return true
    }

    fun cancelAppointmentBooking(appointmentId: String) {
        viewModelScope.launch {
            appointmentRepository.cancelBooking(appointmentId)
        }
    }

    fun completeAppointment(appointmentId: String) {
        viewModelScope.launch {
            appointmentRepository.completeAppointment(appointmentId)
        }
    }

    fun deleteAppointmentSlot(appointmentId: String) {
        viewModelScope.launch {
            appointmentRepository.deleteAppointment(appointmentId)
        }
    }

    fun startChatWith(otherUserId: String) {
        viewModelScope.launch {
            val curr = currentUser.value ?: return@launch
            val isCurrentPsych = curr.role == UserRole.PSYCHOLOGIST
            val psychId = if (isCurrentPsych) curr.id else otherUserId
            val patientId = if (isCurrentPsych) otherUserId else curr.id

            val existing = chatRepository.getChatByParticipants(psychId, patientId)
            if (existing != null) {
                navigateTo(Screen.ChatRoom(existing.chatId))
            } else {
                val generatedId = FirebaseFirestore.getInstance().collection("chats").document().id
                val newChat = ChatModel(
                    chatId = generatedId,
                    psychologistId = psychId,
                    patientId = patientId,
                    lastMessage = "Rozpoczęto nowy czat.",
                    lastMessageTime = System.currentTimeMillis()
                )
                chatRepository.createChat(newChat)
                navigateTo(Screen.ChatRoom(generatedId))
            }
        }
    }
}
