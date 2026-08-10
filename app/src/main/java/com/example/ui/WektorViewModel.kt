package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.UserRole
import com.example.data.local.entity.UserEntity
import com.example.data.local.entity.ChatEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.ReviewEntity
import com.example.data.local.entity.AppointmentEntity
import com.example.data.local.entity.WalletTransactionEntity
import com.example.data.repository.WektorRepository
import com.example.ui.navigation.Screen
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WektorViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = WektorRepository(database)

    // Current Navigation State with a basic backstack
    private val _navigationStack = MutableStateFlow<List<Screen>>(listOf(Screen.Onboarding))
    val currentScreen: StateFlow<Screen> = _navigationStack
        .map { it.lastOrNull() ?: Screen.Onboarding }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Screen.Onboarding)

    val canNavigateBack: StateFlow<Boolean> = _navigationStack
        .map { it.size > 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // UI States
    val currentUser = repository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _showTutorialPrompt = MutableStateFlow(false)
    val showTutorialPrompt: StateFlow<Boolean> = _showTutorialPrompt.asStateFlow()

    fun setShowTutorialPrompt(show: Boolean) {
        _showTutorialPrompt.value = show
    }

    // Search filters state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _roleFilter = MutableStateFlow(UserRole.PSYCHOLOGIST)
    val roleFilter: StateFlow<UserRole> = _roleFilter.asStateFlow()

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
    val filteredPsychologists: StateFlow<List<UserEntity>> = combine(
        repository.getUsersOfRoleFlow(UserRole.PSYCHOLOGIST).combine(repository.getUsersOfRoleFlow(UserRole.PSYCHOLOGY_STUDENT)) { p, s -> p + s },
        _searchQuery,
        _filterAgeMin,
        _filterAgeMax,
        _filterGender,
        _filterSpec,
        _filterVerifiedOnly,
        _filterPriceMax,
        _filterMinRating
    ) { args: Array<Any> ->
        val list = args[0] as List<UserEntity>
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

    suspend fun getUserById(userId: Int): UserEntity? = repository.getUserById(userId)

    val filteredPatients: StateFlow<List<UserEntity>> = combine(
        repository.getUsersOfRoleFlow(UserRole.PATIENT),
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
    val activeChats: StateFlow<List<ChatEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) {
                repository.getChatsForUserFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Favorites
    val favoritePsychologistsIds: StateFlow<Set<Int>> = currentUser
        .flatMapLatest { user ->
            if (user != null) {
                repository.getFavoritesForUserFlow(user.id).map { list -> list.map { it.psychologistId }.toSet() }
            } else {
                flowOf(emptySet())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val favoritePsychologists: StateFlow<List<UserEntity>> = combine(
        currentUser,
        repository.getUsersOfRoleFlow(UserRole.PSYCHOLOGIST).combine(repository.getUsersOfRoleFlow(UserRole.PSYCHOLOGY_STUDENT)) { p, s -> p + s },
        repository.getUsersOfRoleFlow(UserRole.PATIENT),
        favoritePsychologistsIds
    ) { user, allPsychs, allPatients, favIds ->
        val isPsych = user?.role == UserRole.PSYCHOLOGIST || user?.role == UserRole.PSYCHOLOGY_STUDENT
        val targetList = if (isPsych) allPatients else allPsychs
        targetList.filter { targetUser -> favIds.contains(targetUser.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFavorite(psychologistId: Int) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val currentFavs = favoritePsychologistsIds.value
            if (currentFavs.contains(psychologistId)) {
                repository.deleteFavorite(user.id, psychologistId)
            } else {
                repository.insertFavorite(user.id, psychologistId)
            }
        }
    }

    // Map helper to resolve other participants in chats
    private val _chatParticipants = MutableStateFlow<Map<Int, UserEntity>>(emptyMap())
    val chatParticipants = _chatParticipants.asStateFlow()

    init {
        viewModelScope.launch {
            prepopulateMockDataIfNeeded()
            loadChatParticipants()
        }
    }

    private suspend fun loadChatParticipants() {
        // Collect users and cache them for chat views
        repository.getUsersOfRoleFlow(UserRole.PSYCHOLOGIST).combine(repository.getUsersOfRoleFlow(UserRole.PATIENT)) { psychs, patients ->
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
            repository.clearCurrentUser()
            _navigationStack.value = listOf(Screen.Onboarding)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setRoleFilter(role: UserRole) {
        _roleFilter.value = role
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
            kotlinx.coroutines.delay(800)
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
        role: UserRole,
        isVerified: Boolean = false,
        qualifications: String = "",
        specializations: String = "",
        pricePerSession: Double = 0.0,
        bio: String = "",
        customPrices: String = ""
    ) {
        viewModelScope.launch {
            repository.clearCurrentUser()
            val user = UserEntity(
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
            repository.insertUser(user)
            _showTutorialPrompt.value = true
            navigateTo(Screen.MainDashboard)
        }
    }

    fun loginUser(email: String, preferredRole: UserRole? = null) {
        viewModelScope.launch {
            repository.clearCurrentUser()
            val allPsychologists = repository.getUsersOfRoleFlow(UserRole.PSYCHOLOGIST).first()
            val allPatients = repository.getUsersOfRoleFlow(UserRole.PATIENT).first()
            val allStudents = repository.getUsersOfRoleFlow(UserRole.PSYCHOLOGY_STUDENT).first()
            val match = (allPsychologists + allPatients + allStudents).find { it.email.equals(email, ignoreCase = true) }
            
            if (match != null) {
                val updatedMatch = if (preferredRole != null) {
                    match.copy(
                        isCurrentUser = true,
                        role = preferredRole,
                        isVerified = if (preferredRole == UserRole.PSYCHOLOGIST) true else match.isVerified
                    )
                } else {
                    match.copy(isCurrentUser = true)
                }
                repository.updateUser(updatedMatch)
            } else {
                // Auto create demo user to prevent dead ends during exploration
                val isPsych = email.contains("psych", ignoreCase = true)
                val isStud = email.contains("student", ignoreCase = true)
                val detectedRole = if (isPsych) UserRole.PSYCHOLOGIST else if (isStud) UserRole.PSYCHOLOGY_STUDENT else UserRole.PATIENT
                
                val role = preferredRole ?: detectedRole
                val newUser = UserEntity(
                    firstName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                    lastName = "Nowak",
                    age = 29,
                    gender = "Kobieta",
                    phone = "+48 505 444 333",
                    email = email,
                    role = role,
                    isVerified = role == UserRole.PSYCHOLOGIST,
                    qualifications = if (role == UserRole.PSYCHOLOGIST) "Magister Psychologii" else if (role == UserRole.PSYCHOLOGY_STUDENT) "Student 4. roku" else "",
                    bio = "Zarejestrowany użytkownik platformy Wektor.",
                    isCurrentUser = true,
                    customPrices = ""
                )
                repository.insertUser(newUser)
            }
            navigateTo(Screen.MainDashboard)
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
            val curr = currentUser.value
            val targetRole = curr?.role ?: UserRole.PATIENT
            val newPost = UserEntity(
                firstName = firstName,
                lastName = lastName,
                age = age,
                gender = gender,
                phone = curr?.phone ?: "+48 555 000 111",
                email = curr?.email ?: "anonim@wektor.pl",
                role = targetRole,
                isVerified = targetRole == UserRole.PSYCHOLOGIST && qual.isNotEmpty(),
                qualifications = qual,
                specializations = spec,
                pricePerSession = price,
                rating = if (targetRole == UserRole.PSYCHOLOGIST) 5.0 else 0.0,
                ratingCount = if (targetRole == UserRole.PSYCHOLOGIST) 1 else 0,
                bio = bio,
                isCurrentUser = false,
                customPrices = ""
            )
            repository.insertUser(newPost)
            // Automatically switch role tab filter and clear search query so the new post is instantly visible
            val viewRole = if (targetRole == UserRole.PSYCHOLOGY_STUDENT || targetRole == UserRole.PSYCHOLOGIST) UserRole.PSYCHOLOGIST else UserRole.PATIENT
            _roleFilter.value = viewRole
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
            repository.updateUser(updated)
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
            repository.updateUser(updated)
        }
    }

    // Get Chat messages
    fun getMessagesForChat(chatId: Int): Flow<List<MessageEntity>> = repository.getMessagesForChatFlow(chatId)

    // Get Notes (Zametki)
    fun getNotesForChat(chatId: Int): Flow<List<NoteEntity>> = repository.getNotesForChatFlow(chatId)

    fun sendChatMessage(chatId: Int, text: String) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            val msg = MessageEntity(chatId = chatId, senderId = user.id, text = text)
            repository.insertMessage(msg)

            // Update chat last message
            val chatList = activeChats.value
            val chat = chatList.find { it.chatId == chatId }
            if (chat != null) {
                repository.updateChat(chat.copy(lastMessage = text, lastMessageTime = System.currentTimeMillis()))
            }
        }
    }

    fun saveClientNote(chatId: Int, title: String, content: String) {
        viewModelScope.launch {
            val note = NoteEntity(chatId = chatId, title = title, content = content)
            repository.insertNote(note)
        }
    }

    fun deleteClientNote(noteId: Int) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
        }
    }

    // Get reviews flow for detail view
    fun getReviewsForPsychologist(psychologistId: Int): Flow<List<ReviewEntity>> = repository.getReviewsForPsychologistFlow(psychologistId)

    fun addReview(psychologistId: Int, rating: Int, comment: String) {
        viewModelScope.launch {
            val current = currentUser.value
            val reviewerName = if (current != null) "${current.firstName} ${current.lastName}" else "Anonim"
            repository.insertReview(ReviewEntity(psychologistId = psychologistId, reviewerName = reviewerName, rating = rating, comment = comment))

            // Recalculate psych rating
            val p = repository.getUserById(psychologistId) ?: return@launch
            val newCount = p.ratingCount + 1
            val newRating = ((p.rating * p.ratingCount) + rating) / newCount
            repository.updateUser(p.copy(rating = newRating, ratingCount = newCount))
        }
    }

    // --- APPOINTMENTS / CALENDAR STATE & ACTIONS ---
    val currentPsychologistAppointments: StateFlow<List<AppointmentEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null && (user.role == UserRole.PSYCHOLOGIST || user.role == UserRole.PSYCHOLOGY_STUDENT)) {
                repository.getAppointmentsForPsychologistFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPatientAppointments: StateFlow<List<AppointmentEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.role == UserRole.PATIENT) {
                repository.getAppointmentsForPatientFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val walletTransactions: StateFlow<List<WalletTransactionEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) {
                repository.getWalletTransactionsFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun topUpWallet(amount: Int) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val transaction = WalletTransactionEntity(
                userId = user.id,
                amount = amount,
                title = "Doładowanie portfela"
            )
            repository.insertWalletTransaction(transaction)
            repository.updateUser(user.copy(coinsBalance = user.coinsBalance + amount))
        }
    }

    fun spendCoins(amount: Int, title: String): Boolean {
        val user = currentUser.value ?: return false
        if (user.coinsBalance < amount) return false
        viewModelScope.launch {
            val transaction = WalletTransactionEntity(
                userId = user.id,
                amount = -amount,
                title = title
            )
            repository.insertWalletTransaction(transaction)
            repository.updateUser(user.copy(coinsBalance = user.coinsBalance - amount))
        }
        return true
    }

    fun getFreeSlotsForPsychologist(psychId: Int): Flow<List<AppointmentEntity>> {
        return repository.getFreeSlotsForPsychologistFlow(psychId)
    }

    fun addFreeSlot(date: String, time: String) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.insertAppointment(
                AppointmentEntity(
                    psychologistId = user.id,
                    date = date,
                    time = time,
                    status = "FREE"
                )
            )
        }
    }

    fun bookAppointment(appointmentId: Int, notes: String = "", costInCoins: Int = 40): Boolean {
        val user = currentUser.value ?: return false
        if (user.coinsBalance < costInCoins) return false
        viewModelScope.launch {
            // Deduct coins
            val transaction = WalletTransactionEntity(
                userId = user.id,
                amount = -costInCoins,
                title = "Opłata za wizytę ($notes)"
            )
            repository.insertWalletTransaction(transaction)
            repository.updateUser(user.copy(coinsBalance = user.coinsBalance - costInCoins))

            // Book slot
            repository.bookAppointment(appointmentId, user.id, notes)
        }
        return true
    }

    fun cancelAppointmentBooking(appointmentId: Int) {
        viewModelScope.launch {
            repository.cancelBooking(appointmentId)
        }
    }

    fun completeAppointment(appointmentId: Int) {
        viewModelScope.launch {
            repository.completeAppointment(appointmentId)
        }
    }

    fun deleteAppointmentSlot(appointmentId: Int) {
        viewModelScope.launch {
            repository.deleteAppointment(appointmentId)
        }
    }

    fun startChatWith(otherUserId: Int) {
        viewModelScope.launch {
            val curr = currentUser.value ?: return@launch
            val isCurrentPsych = curr.role == UserRole.PSYCHOLOGIST
            val psychId = if (isCurrentPsych) curr.id else otherUserId
            val patientId = if (isCurrentPsych) otherUserId else curr.id

            val existing = repository.getChatByParticipants(psychId, patientId)
            if (existing != null) {
                navigateTo(Screen.ChatRoom(existing.chatId))
            } else {
                val newChat = ChatEntity(
                    psychologistId = psychId,
                    patientId = patientId,
                    lastMessage = "Rozpoczęto nowy czat.",
                    lastMessageTime = System.currentTimeMillis()
                )
                val newId = repository.createChat(newChat)
                navigateTo(Screen.ChatRoom(newId.toInt()))
            }
        }
    }

    private suspend fun prepopulateMockDataIfNeeded() {
        val existing = repository.getUsersOfRoleFlow(UserRole.PSYCHOLOGIST).first()
        if (existing.isNotEmpty()) return

        // 1. Insert Mock Psychologists
        val p1Id = repository.insertUser(UserEntity(
            firstName = "Anna",
            lastName = "Nowak",
            age = 34,
            gender = "Kobieta",
            phone = "+48 501 234 567",
            email = "anna.nowak@wektor.pl",
            role = UserRole.PSYCHOLOGIST,
            isVerified = true,
            qualifications = "Magister Psychologii UJ",
            specializations = "Rodzina, Depresja, Lęki",
            pricePerSession = 150.0,
            rating = 4.8,
            ratingCount = 8,
            bio = "Jestem psychologiem z 8-letnim doświadczeniem w pracy z rodzinami oraz osobami cierpiącymi na depresję i stany lękowe. Pomagam odnaleźć wewnętrzny wektor rozwoju.",
            isCurrentUser = false
        )).toInt()

        val p2Id = repository.insertUser(UserEntity(
            firstName = "Igor",
            lastName = "Kowalski",
            age = 24,
            gender = "Mężczyzna",
            phone = "+48 602 345 678",
            email = "igor.k@student.pl",
            role = UserRole.PSYCHOLOGY_STUDENT,
            isVerified = false,
            qualifications = "Student 4. roku UW",
            specializations = "Ogólny, Praca, Stres",
            pricePerSession = 80.0,
            rating = 4.2,
            ratingCount = 3,
            bio = "Pasjonat psychologii klinicznej. Jako student oferuję niedrogie konsultacje, wspierając w radzeniu sobie ze stresem zawodowym i akademickim.",
            isCurrentUser = false
        )).toInt()

        val p3Id = repository.insertUser(UserEntity(
            firstName = "Maria",
            lastName = "Wiśniewska",
            age = 29,
            gender = "Kobieta",
            phone = "+48 703 456 789",
            email = "m.wisniewska@phd.pl",
            role = UserRole.PSYCHOLOGIST,
            isVerified = true,
            qualifications = "Doktorantka SWPS",
            specializations = "Życie intymne, Relacje, Emocje",
            pricePerSession = 180.0,
            rating = 4.9,
            ratingCount = 12,
            bio = "Specjalizuję się w psychoterapii relacji i sfery intymnej. Prowadzę badania nad dynamiką emocjonalną w związkach partnerskich.",
            isCurrentUser = false
        )).toInt()

        val p4Id = repository.insertUser(UserEntity(
            firstName = "Piotr",
            lastName = "Zieliński",
            age = 42,
            gender = "Mężczyzna",
            phone = "+48 804 567 890",
            email = "p.zielinski@terapia.pl",
            role = UserRole.PSYCHOLOGIST,
            isVerified = true,
            qualifications = "Certyfikowany Psychoterapeuta PTTPB",
            specializations = "Uzależnienia, Rodzina, Kryzysy",
            pricePerSession = 220.0,
            rating = 4.5,
            ratingCount = 5,
            bio = "Certyfikowany terapeuta poznawczo-behawioralny. Pomagam przejść przez najtrudniejsze kryzysy życiowe i uzależnienia.",
            isCurrentUser = false
        )).toInt()

        // 2. Insert Mock Patients (with concern descriptions)
        val pat1Id = repository.insertUser(UserEntity(
            firstName = "Janusz",
            lastName = "Kowal",
            age = 28,
            gender = "Mężczyzna",
            phone = "+48 905 678 901",
            email = "janusz.it@poczta.pl",
            role = UserRole.PATIENT,
            bio = "Szukam wsparcia w związku z wypaleniem zawodowym w IT oraz przewlekłym stresem.",
            isCurrentUser = false
        )).toInt()

        val pat2Id = repository.insertUser(UserEntity(
            firstName = "Julia",
            lastName = "Malinowska",
            age = 21,
            gender = "Kobieta",
            phone = "+48 106 789 012",
            email = "julia.m@stud.pl",
            role = UserRole.PATIENT,
            bio = "Zmagam się ze stanami lękowymi przed egzaminami oraz trudnościami w relacjach rówieśniczych.",
            isCurrentUser = false
        )).toInt()

        // 3. Insert Reviews for Anna Novak
        repository.insertReview(ReviewEntity(psychologistId = p1Id, reviewerName = "Tomasz B.", rating = 5, comment = "Bardzo profesjonalna pomoc. Sesje pomogły mi odbudować relacje z synem. Gorąco polecam!"))
        repository.insertReview(ReviewEntity(psychologistId = p1Id, reviewerName = "Anna K.", rating = 5, comment = "Ciepła, empatyczna i niezwykle merytoryczna pani psycholog."))
        repository.insertReview(ReviewEntity(psychologistId = p1Id, reviewerName = "Krzysztof", rating = 4, comment = "Dobra komunikacja i konkretne porady, chociaż ceny mogłyby być nieco niższe."))
        repository.insertReview(ReviewEntity(psychologistId = p1Id, reviewerName = "Karolina", rating = 5, comment = "Pani Anna uratowała nasze małżeństwo! Wspaniała praca nad komunikacją."))
        repository.insertReview(ReviewEntity(psychologistId = p1Id, reviewerName = "Marek S.", rating = 4, comment = "Rzeczowa pomoc w kryzysie zawodowym. Polecam każdemu."))

        // Reviews for Maria Wiśniewska
        repository.insertReview(ReviewEntity(psychologistId = p3Id, reviewerName = "Zofia", rating = 5, comment = "Cudowna atmosfera! Pani Maria potrafi otworzyć nawet najbardziej skrytą osobę."))
        repository.insertReview(ReviewEntity(psychologistId = p3Id, reviewerName = "Robert", rating = 5, comment = "Niezwykłe wyczucie tematów intymnych. Czuję ogromną ulgę po sesjach."))

        // 4. Create Initial Chat and Message history
        val chat1Id = repository.createChat(ChatEntity(
            psychologistId = p1Id,
            patientId = pat1Id,
            lastMessage = "Czekam na nasze kolejne spotkanie w środę.",
            lastMessageTime = System.currentTimeMillis() - 3600000
        )).toInt()

        repository.insertMessage(MessageEntity(chatId = chat1Id, senderId = pat1Id, text = "Dzień dobry, mam problem z zasypianiem przez stres w biurze.", timestamp = System.currentTimeMillis() - 7200000))
        repository.insertMessage(MessageEntity(chatId = chat1Id, senderId = p1Id, text = "Rozumiem Januszu. Na początek wypróbujmy technikę oddechową 4-7-8 przed snem.", timestamp = System.currentTimeMillis() - 5400000))
        repository.insertMessage(MessageEntity(chatId = chat1Id, senderId = pat1Id, text = "Przetestowałem ją, jest lekka poprawa. Chciałbym omówić to na sesji.", timestamp = System.currentTimeMillis() - 4500000))
        repository.insertMessage(MessageEntity(chatId = chat1Id, senderId = p1Id, text = "Czekam na nasze kolejne spotkanie w środę.", timestamp = System.currentTimeMillis() - 3600000))

        // Create some default notes for this client chat (Zametki)
        repository.insertNote(NoteEntity(chatId = chat1Id, title = "Sesja 1: Wypalenie i sen", content = "Pacjent Janusz skarży się na bezsenność spowodowaną deadline'ami. Praca w korporacji IT. Zaproponowano technikę 4-7-8 i ograniczenie ekranów przed snem."))
        repository.insertNote(NoteEntity(chatId = chat1Id, title = "Plan działania", content = "1. Praktyka mindfullness 10 min dziennie.\n2. Rozmowa o granicach w pracy z managerem.\n3. Monitorowanie jakości snu."))

        // 5. Prepopulate Calendar appointments
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        fun getRelativeDate(daysOffset: Int): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, daysOffset)
            return sdf.format(cal.time)
        }

        // Future booked appointment
        repository.insertAppointment(
            AppointmentEntity(
                psychologistId = p1Id,
                patientId = pat1Id,
                date = getRelativeDate(1),
                time = "10:00",
                notes = "Kontynuacja tematu bezsenności i technik oddechowych.",
                status = "BOOKED"
            )
        )
        // Future free slots for Anna Nowak
        repository.insertAppointment(AppointmentEntity(psychologistId = p1Id, date = getRelativeDate(1), time = "12:00", status = "FREE"))
        repository.insertAppointment(AppointmentEntity(psychologistId = p1Id, date = getRelativeDate(1), time = "15:30", status = "FREE"))
        repository.insertAppointment(AppointmentEntity(psychologistId = p1Id, date = getRelativeDate(2), time = "09:00", status = "FREE"))
        repository.insertAppointment(AppointmentEntity(psychologistId = p1Id, date = getRelativeDate(2), time = "11:00", status = "FREE"))

        // Booked appointment for Anna Nowak with Julia Malinowska
        repository.insertAppointment(
            AppointmentEntity(
                psychologistId = p1Id,
                patientId = pat2Id,
                date = getRelativeDate(3),
                time = "14:00",
                notes = "Praca z lękiem przedegzaminacyjnym.",
                status = "BOOKED"
            )
        )

        // Completed appointment
        repository.insertAppointment(
            AppointmentEntity(
                psychologistId = p1Id,
                patientId = pat1Id,
                date = getRelativeDate(-2),
                time = "15:00",
                notes = "Sesja wstępna. Omówienie kontraktu terapeutycznego.",
                status = "COMPLETED"
            )
        )

        // Free slots for other psychologists
        repository.insertAppointment(AppointmentEntity(psychologistId = p2Id, date = getRelativeDate(1), time = "10:00", status = "FREE"))
        repository.insertAppointment(AppointmentEntity(psychologistId = p2Id, date = getRelativeDate(1), time = "13:00", status = "FREE"))
        repository.insertAppointment(AppointmentEntity(psychologistId = p3Id, date = getRelativeDate(2), time = "16:00", status = "FREE"))
    }
}
