package com.example.stellare.ui.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val appointmentRepository: AppointmentRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val psychologistAppointments: StateFlow<List<AppointmentModel>> = currentUser
        .flatMapLatest { user ->
            if (user != null && (user.role == UserRole.PSYCHOLOGIST || user.role == UserRole.PSYCHOLOGY_STUDENT)) {
                appointmentRepository.getAppointmentsForPsychologistFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val patientAppointments: StateFlow<List<AppointmentModel>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.role == UserRole.PATIENT) {
                appointmentRepository.getAppointmentsForPatientFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _bookingState = MutableStateFlow<BookingState>(BookingState.Idle)
    val bookingState: StateFlow<BookingState> = _bookingState.asStateFlow()

    fun resetBookingState() {
        _bookingState.value = BookingState.Idle
    }

    fun freeSlotsFor(psychologistId: String): Flow<List<AppointmentModel>> =
        appointmentRepository.getFreeSlotsForPsychologistFlow(psychologistId)

    fun addFreeSlot(date: String, time: String) {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser() ?: return@launch
            try {
                appointmentRepository.insertAppointment(
                    AppointmentModel(
                        psychologistId = user.id,
                        date = date,
                        time = time,
                        status = "FREE",
                        priceCoins = user.pricePerSession.toInt()
                    )
                )
            } catch (e: Exception) {
                Log.e("Calendar", "Nie udalo sie wystawic terminu", e)
            }
        }
    }

    fun book(appointment: AppointmentModel, notes: String = "") {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            if (user == null) {
                _bookingState.value = BookingState.Error("Brak zalogowanego użytkownika.")
                return@launch
            }
            if (appointment.priceCoins <= 0) {
                _bookingState.value = BookingState.Error(
                    "Termin nie ma ustalonej ceny. Poproś psychologa o wystawienie go ponownie."
                )
                return@launch
            }
            _bookingState.value = BookingState.InProgress
            _bookingState.value = try {
                appointmentRepository.bookAppointment(
                    appointmentId = appointment.appointmentId,
                    patientId = user.id,
                    psychologistId = appointment.psychologistId,
                    priceCoins = appointment.priceCoins,
                    notes = notes
                )
                BookingState.Success
            } catch (e: Exception) {
                Log.e("Booking", "Rezerwacja nieudana", e)
                BookingState.Error(e.message ?: "Nie udało się zarezerwować terminu.")
            }
        }
    }

    fun cancel(appointmentId: String) {
        viewModelScope.launch {
            try {
                appointmentRepository.cancelBooking(appointmentId)
            } catch (e: Exception) {
                Log.e("Calendar", "Nie udalo sie anulowac rezerwacji", e)
            }
        }
    }

    fun complete(appointmentId: String) {
        viewModelScope.launch {
            try {
                appointmentRepository.completeAppointment(appointmentId)
            } catch (e: Exception) {
                Log.e("Calendar", "Nie udalo sie zakonczyc wizyty", e)
            }
        }
    }

    fun deleteSlot(appointmentId: String) {
        viewModelScope.launch {
            try {
                appointmentRepository.deleteAppointment(appointmentId)
            } catch (e: Exception) {
                Log.e("Calendar", "Nie udalo sie usunac terminu", e)
            }
        }
    }
}
