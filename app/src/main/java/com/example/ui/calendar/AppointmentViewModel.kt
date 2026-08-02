package com.example.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AppointmentEntity
import com.example.data.local.entity.WalletTransactionEntity
import com.example.data.repository.AppointmentRepository
import com.example.data.repository.UserRepository
import com.example.data.repository.WalletRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppointmentViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val appointmentRepository = AppointmentRepository(database.appointmentDao())
    private val userRepository = UserRepository(database.userDao(), database.favoriteDao())
    private val walletRepository = WalletRepository(database.walletTransactionDao())

    val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentPsychologistAppointments: StateFlow<List<AppointmentEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null && (user.role == "PSYCHOLOGIST" || user.role == "STUDENT")) {
                appointmentRepository.getAppointmentsForPsychologistFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPatientAppointments: StateFlow<List<AppointmentEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.role == "PATIENT") {
                appointmentRepository.getAppointmentsForPatientFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getFreeSlotsForPsychologist(psychId: Int): Flow<List<AppointmentEntity>> {
        return appointmentRepository.getFreeSlotsForPsychologistFlow(psychId)
    }

    fun addFreeSlot(date: String, time: String) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            appointmentRepository.insertAppointment(
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
            walletRepository.insertWalletTransaction(transaction)
            userRepository.updateUser(user.copy(coinsBalance = user.coinsBalance - costInCoins))

            // Book slot
            appointmentRepository.bookAppointment(appointmentId, user.id, notes)
        }
        return true
    }

    fun cancelAppointmentBooking(appointmentId: Int) {
        viewModelScope.launch {
            appointmentRepository.cancelBooking(appointmentId)
        }
    }

    fun completeAppointment(appointmentId: Int) {
        viewModelScope.launch {
            appointmentRepository.completeAppointment(appointmentId)
        }
    }

    fun deleteAppointmentSlot(appointmentId: Int) {
        viewModelScope.launch {
            appointmentRepository.deleteAppointment(appointmentId)
        }
    }
}
