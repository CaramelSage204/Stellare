package com.example.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.UserRole
import com.example.data.local.entity.UserEntity
import com.example.data.repository.UserRepository
import com.example.ui.navigation.Screen
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SessionViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val userRepository = UserRepository(database.userDao(), database.favoriteDao())

    private val _navigationStack = MutableStateFlow<List<Screen>>(listOf(Screen.Onboarding))
    val currentScreen: StateFlow<Screen> = _navigationStack
        .map { it.lastOrNull() ?: Screen.Onboarding }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Screen.Onboarding)

    val canNavigateBack: StateFlow<Boolean> = _navigationStack
        .map { it.size > 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
            userRepository.clearCurrentUser()
            _navigationStack.value = listOf(Screen.Onboarding)
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
            userRepository.clearCurrentUser()
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
            userRepository.insertUser(user)
            // Note: DashboardViewModel manages showTutorialPrompt, but login/register happens here.
            // In a real app we'd use a shared event or state. Verification of requirement...
            // verbatim move means I might need to keep showTutorialPrompt if it's strictly session-ish?
            // The prompt says DashboardViewModel gets showTutorialPrompt.
            navigateTo(Screen.MainDashboard)
        }
    }

    fun loginUser(email: String, preferredRole: UserRole? = null) {
        viewModelScope.launch {
            userRepository.clearCurrentUser()
            val allPsychologists = userRepository.getUsersOfRoleFlow(UserRole.PSYCHOLOGIST).first()
            val allPatients = userRepository.getUsersOfRoleFlow(UserRole.PATIENT).first()
            val allStudents = userRepository.getUsersOfRoleFlow(UserRole.PSYCHOLOGY_STUDENT).first()
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
                userRepository.updateUser(updatedMatch)
            } else {
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
                userRepository.insertUser(newUser)
            }
            navigateTo(Screen.MainDashboard)
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
}
