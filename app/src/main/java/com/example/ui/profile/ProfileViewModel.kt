package com.example.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val userRepository = UserRepository(database.userDao(), database.favoriteDao())

    val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateProfile(
        firstName: String,
        lastName: String,
        age: Int,
        gender: String,
        bio: String,
        specializations: String,
        pricePerSession: Double,
        customPrices: String = "",
        profileImageUri: String? = null,
        onSuccess: () -> Unit
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
            onSuccess()
        }
    }
}
