package com.example.ui.psychologistdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UserEntity
import com.example.data.repository.UserRepository

class PsychologistDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val userRepository = UserRepository(database.userDao(), database.favoriteDao())

    suspend fun getUserById(userId: Int): UserEntity? = userRepository.getUserById(userId)
}
