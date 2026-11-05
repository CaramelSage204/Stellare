package com.example.stellare.ui.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentOrder
import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.ui.StaleData
import com.example.stellare.ui.lastKnownUser
import com.example.stellare.ui.UncertainWriteMessages
import com.example.stellare.ui.UserFacingError
import com.example.stellare.ui.WriteOutcome
import com.example.stellare.ui.WriteReconciliation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val appointmentRepository: AppointmentRepository,
    private val userRepository: UserRepository,
    private val timeZone: () -> TimeZone,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val currentUser = userRepository.getCurrentUserFlow()
        .lastKnownUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val appointmentsStale = StaleData(APPOINTMENTS_STALE_MESSAGE)
    val appointmentsStaleMessage: StateFlow<String?> = appointmentsStale.message

    fun retryAppointments() {
        appointmentsStale.retry()
    }

    val psychologistAppointments: StateFlow<List<AppointmentModel>> = appointmentsStale
        .cover("Calendar", "wizyt psychologa") {
            userRepository.whileSignedIn(emptyList()) {
                currentUser.flatMapLatest { user ->
                    if (user != null && (user.role == UserRole.PSYCHOLOGIST || user.role == UserRole.PSYCHOLOGY_STUDENT)) {
                        appointmentRepository.getAppointmentsForPsychologistFlow(user.id).map(AppointmentOrder::chronological)
                    } else {
                        flowOf(emptyList())
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val patientAppointments: StateFlow<List<AppointmentModel>> = appointmentsStale
        .cover("Calendar", "wizyt pacjenta") {
            userRepository.whileSignedIn(emptyList()) {
                currentUser.flatMapLatest { user ->
                    if (user != null && user.role == UserRole.PATIENT) {
                        appointmentRepository.getAppointmentsForPatientFlow(user.id).map(AppointmentOrder::chronological)
                    } else {
                        flowOf(emptyList())
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val participantNames: StateFlow<Map<String, String>> = userRepository
        .whileSignedIn(emptyMap()) {
            combine(psychologistAppointments, patientAppointments) { asPsychologist, asPatient ->
                asPsychologist.mapNotNull { it.patientId }.toSet() + asPatient.map { it.psychologistId }
            }.map { ids -> userRepository.namesOf(ids) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _selectedDayMillis = MutableStateFlow(clock())
    val selectedDayMillis: StateFlow<Long> = _selectedDayMillis.asStateFlow()

    fun selectDay(millis: Long) {
        _selectedDayMillis.value = millis
    }

    fun nowMillis(): Long = clock()

    private val _bookingState = MutableStateFlow<BookingState>(BookingState.Idle)
    val bookingState: StateFlow<BookingState> = _bookingState.asStateFlow()

    fun resetBookingState() {
        _bookingState.value = BookingState.Idle
    }

    fun freeSlotsFor(psychologistId: String): Flow<List<AppointmentModel>> =
        appointmentsStale.cover("Calendar", "wolnych terminow") {
            userRepository.whileSignedIn(emptyList()) {
                appointmentRepository.getFreeSlotsForPsychologistFlow(psychologistId)
                    .map { slots -> AppointmentOrder.upcoming(slots, clock()) }
            }
        }

    fun addFreeSlot(startsAtMillis: Long) {
        viewModelScope.launch {
            val user = when (val read = userRepository.readCurrentUser()) {
                is CurrentUserRead.Loaded -> read.user
                CurrentUserRead.SignedOut, CurrentUserRead.Absent -> return@launch
                is CurrentUserRead.Unavailable -> {
                    Log.e("Calendar", "Nie udalo sie odczytac profilu przed wystawieniem terminu", read.error)
                    _transferState.value = TransferState.Failed(
                        UserFacingError.message(read.error, UserFacingError.PROFILE_READ_FAILED)
                    )
                    return@launch
                }
            }
            try {
                appointmentRepository.insertAppointment(
                    AppointmentModel.freeSlot(
                        psychologistId = user.id,
                        startsAtMillis = startsAtMillis,
                        priceCoins = user.pricePerSession.toInt(),
                        zone = timeZone()
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Calendar", "Nie udalo sie wystawic terminu", e)
            }
        }
    }

    fun book(appointment: AppointmentModel, notes: String = "") {
        if (_bookingState.value is BookingState.InProgress) return
        _bookingState.value = BookingState.InProgress
        viewModelScope.launch {
            val user = when (val read = userRepository.readCurrentUser()) {
                is CurrentUserRead.Loaded -> read.user
                CurrentUserRead.SignedOut, CurrentUserRead.Absent -> {
                    _bookingState.value = BookingState.Error("Brak zalogowanego użytkownika.")
                    return@launch
                }
                is CurrentUserRead.Unavailable -> {
                    Log.e("Booking", "Nie udalo sie odczytac profilu przed rezerwacja", read.error)
                    _bookingState.value = BookingState.Error(
                        UserFacingError.message(read.error, UserFacingError.PROFILE_READ_FAILED)
                    )
                    return@launch
                }
            }
            if (appointment.priceCoins <= 0) {
                _bookingState.value = BookingState.Error(
                    "Termin nie ma ustalonej ceny. Poproś psychologa o wystawienie go ponownie."
                )
                return@launch
            }
            if (appointment.startsAtMillis <= clock()) {
                _bookingState.value = BookingState.Error("Ten termin już minął. Wybierz inny.")
                return@launch
            }
            val outcome = WriteReconciliation.attempt(
                write = {
                    appointmentRepository.bookAppointment(
                        appointmentId = appointment.appointmentId,
                        patientId = user.id,
                        psychologistId = appointment.psychologistId,
                        priceCoins = appointment.priceCoins,
                        notes = notes
                    )
                },
                landed = { Unit.takeIf { appointmentRepository.bookingLanded(appointment.appointmentId, user.id) } },
                failureMessage = "Nie udało się zarezerwować terminu. Spróbuj ponownie.",
                uncertainMessage = UncertainWriteMessages.BOOKING,
                onError = { Log.e("Booking", "Rezerwacja nieudana", it) }
            )
            _bookingState.value = when (outcome) {
                is WriteOutcome.Succeeded -> BookingState.Success
                is WriteOutcome.Failed -> BookingState.Error(outcome.message)
                is WriteOutcome.Unknown -> BookingState.Error(outcome.message)
            }
        }
    }

    private val _cancelOutcome = MutableStateFlow<CancelOutcome?>(null)
    val cancelOutcome: StateFlow<CancelOutcome?> = _cancelOutcome.asStateFlow()

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    fun resetTransferState() {
        _transferState.value = TransferState.Idle
    }

    fun depositOf(appointmentId: String): Flow<DepositState> =
        userRepository.whileSignedIn<DepositState>(DepositState.Loading) {
            appointmentRepository.observeEscrow(appointmentId)
                .map<EscrowModel?, DepositState> { DepositState.Known(it) }
                .catch { emit(DepositState.Unavailable) }
        }

    fun reclaim(appointmentId: String) = runTransfer(
        fallbackMessage = "Nie udało się odzyskać monet.",
        uncertainMessage = UncertainWriteMessages.RECLAIM,
        completed = TransferState::Reclaimed,
        landed = { appointmentRepository.reclaimLanded(appointmentId) }
    ) { userId ->
        appointmentRepository.refundAbandonedBooking(appointmentId, userId)
    }

    fun cancel(appointmentId: String) = runTransfer(
        fallbackMessage = "Nie udało się odwołać wizyty.",
        uncertainMessage = UncertainWriteMessages.CANCELLATION,
        completed = TransferState::Cancelled,
        landed = { userId -> appointmentRepository.cancellationLanded(appointmentId, userId) }
    ) { userId ->
        appointmentRepository.cancelBooking(appointmentId, userId)
    }

    private fun <T : Any> runTransfer(
        fallbackMessage: String,
        uncertainMessage: String,
        completed: (T) -> TransferState,
        landed: suspend (userId: String) -> T?,
        action: suspend (userId: String) -> T
    ) {
        if (_transferState.value == TransferState.InProgress) return
        _transferState.value = TransferState.InProgress
        viewModelScope.launch {
            val user = when (val read = userRepository.readCurrentUser()) {
                is CurrentUserRead.Loaded -> read.user
                CurrentUserRead.SignedOut, CurrentUserRead.Absent -> {
                    _transferState.value = TransferState.Failed("Brak zalogowanego użytkownika.")
                    return@launch
                }
                is CurrentUserRead.Unavailable -> {
                    Log.e("Calendar", "Nie udalo sie odczytac profilu przed przelewem monet", read.error)
                    _transferState.value = TransferState.Failed(
                        UserFacingError.message(read.error, UserFacingError.PROFILE_READ_FAILED)
                    )
                    return@launch
                }
            }
            val outcome = WriteReconciliation.attempt(
                write = { action(user.id) },
                landed = { landed(user.id) },
                failureMessage = fallbackMessage,
                uncertainMessage = uncertainMessage,
                onError = { Log.e("Calendar", fallbackMessage, it) }
            )
            _transferState.value = when (outcome) {
                is WriteOutcome.Succeeded -> {
                    if (outcome.value is CancelOutcome) _cancelOutcome.value = outcome.value
                    completed(outcome.value)
                }
                is WriteOutcome.Failed -> TransferState.Failed(outcome.message)
                is WriteOutcome.Unknown -> TransferState.Failed(outcome.message)
            }
        }
    }

    fun complete(appointmentId: String) {
        viewModelScope.launch {
            val outcome = WriteReconciliation.attempt(
                write = { appointmentRepository.completeAppointment(appointmentId) },
                landed = { appointmentRepository.completionLanded(appointmentId) },
                failureMessage = "Nie udało się zakończyć wizyty.",
                uncertainMessage = UncertainWriteMessages.COMPLETION,
                onError = { Log.e("Calendar", "Nie udalo sie zakonczyc wizyty", it) }
            )
            _transferState.value = when (outcome) {
                is WriteOutcome.Succeeded -> TransferState.Completed(outcome.value)
                is WriteOutcome.Failed -> TransferState.Failed(outcome.message)
                is WriteOutcome.Unknown -> TransferState.Failed(outcome.message)
            }
        }
    }

    fun deleteSlot(appointmentId: String) {
        viewModelScope.launch {
            try {
                appointmentRepository.deleteAppointment(appointmentId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Calendar", "Nie udalo sie usunac terminu", e)
            }
        }
    }

    private companion object {
        const val APPOINTMENTS_STALE_MESSAGE =
            "Nie udało się odświeżyć terminów. Pokazane wizyty mogą być nieaktualne."
    }
}
