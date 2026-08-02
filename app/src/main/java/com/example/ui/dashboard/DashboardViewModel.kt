package com.example.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UserEntity
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val userRepository = UserRepository(database.userDao(), database.favoriteDao())

    val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _showTutorialPrompt = MutableStateFlow(false)
    val showTutorialPrompt: StateFlow<Boolean> = _showTutorialPrompt.asStateFlow()

    fun setShowTutorialPrompt(show: Boolean) {
        _showTutorialPrompt.value = show
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _roleFilter = MutableStateFlow("PSYCHOLOGIST")
    val roleFilter: StateFlow<String> = _roleFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _filterAgeMin = MutableStateFlow(18)
    val filterAgeMin = _filterAgeMin.asStateFlow()

    private val _filterAgeMax = MutableStateFlow(100)
    val filterAgeMax = _filterAgeMax.asStateFlow()

    private val _filterGender = MutableStateFlow("Wszystkie")
    val filterGender = _filterGender.asStateFlow()

    private val _filterSpec = MutableStateFlow("Wszystkie")
    val filterSpec = _filterSpec.asStateFlow()

    private val _filterVerifiedOnly = MutableStateFlow(false)
    val filterVerifiedOnly = _filterVerifiedOnly.asStateFlow()

    private val _filterPriceMax = MutableStateFlow(400.0)
    val filterPriceMax = _filterPriceMax.asStateFlow()

    private val _filterMinRating = MutableStateFlow(0.0)
    val filterMinRating = _filterMinRating.asStateFlow()

    val filteredPsychologists: StateFlow<List<UserEntity>> = combine(
        userRepository.getUsersOfRoleFlow("PSYCHOLOGIST").combine(userRepository.getUsersOfRoleFlow("STUDENT")) { p, s -> p + s },
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

    val filteredPatients: StateFlow<List<UserEntity>> = combine(
        userRepository.getUsersOfRoleFlow("PATIENT"),
        _searchQuery
    ) { list, query ->
        list.filter { user ->
            val matchesQuery = query.isEmpty() || 
                    "${user.firstName} ${user.lastName}".contains(query, ignoreCase = true) ||
                    user.bio.contains(query, ignoreCase = true)
            matchesQuery
        }.sortedByDescending { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritePsychologistsIds: StateFlow<Set<Int>> = currentUser
        .flatMapLatest { user ->
            if (user != null) {
                userRepository.getFavoritesForUserFlow(user.id).map { list -> list.map { it.psychologistId }.toSet() }
            } else {
                flowOf(emptySet())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val favoritePsychologists: StateFlow<List<UserEntity>> = combine(
        currentUser,
        userRepository.getUsersOfRoleFlow("PSYCHOLOGIST").combine(userRepository.getUsersOfRoleFlow("STUDENT")) { p, s -> p + s },
        userRepository.getUsersOfRoleFlow("PATIENT"),
        favoritePsychologistsIds
    ) { user, allPsychs, allPatients, favIds ->
        val isPsych = user?.role == "PSYCHOLOGIST" || user?.role == "STUDENT"
        val targetList = if (isPsych) allPatients else allPsychs
        targetList.filter { targetUser -> favIds.contains(targetUser.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFavorite(psychologistId: Int) {
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

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setRoleFilter(role: String) {
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
            val targetRole = curr?.role ?: "PATIENT"
            val newPost = UserEntity(
                firstName = firstName,
                lastName = lastName,
                age = age,
                gender = gender,
                phone = curr?.phone ?: "+48 555 000 111",
                email = curr?.email ?: "anonim@wektor.pl",
                role = targetRole,
                isVerified = targetRole == "PSYCHOLOGIST" && qual.isNotEmpty(),
                qualifications = qual,
                specializations = spec,
                pricePerSession = price,
                rating = if (targetRole == "PSYCHOLOGIST") 5.0 else 0.0,
                ratingCount = if (targetRole == "PSYCHOLOGIST") 1 else 0,
                bio = bio,
                isCurrentUser = false,
                customPrices = ""
            )
            userRepository.insertUser(newPost)
            val viewRole = if (targetRole == "STUDENT" || targetRole == "PSYCHOLOGIST") "PSYCHOLOGIST" else "PATIENT"
            _roleFilter.value = viewRole
            _searchQuery.value = ""
        }
    }
}
