package com.example.stellare.ui.verification

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.VerificationRequestModel
import com.example.stellare.data.model.VerificationStatus
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.VerificationRepository
import com.example.stellare.ui.StaleData
import com.example.stellare.ui.UserFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

internal const val REQUESTS_STALE_MESSAGE =
    "Nie udało się odświeżyć zgłoszeń. Pokazany stan może być nieaktualny."
internal const val DECISION_FAILED_MESSAGE =
    "Nie udało się zapisać decyzji. Zgłoszenie zostało bez zmian. Spróbuj ponownie."
private const val NOT_SIGNED_IN_MESSAGE = "Brak zalogowanego użytkownika."

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationViewModel(
    private val verificationRepository: VerificationRepository,
    private val userRepository: UserRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val requestsStale = StaleData(REQUESTS_STALE_MESSAGE)
    val requestsStaleMessage: StateFlow<String?> = requestsStale.message

    fun retryRequests() {
        requestsStale.retry()
    }

    val myRequest: StateFlow<VerificationRequestModel?> = requestsStale
        .cover("Verification", "wlasnego zgloszenia") {
            userRepository.signedInUserIdFlow().flatMapLatest { uid ->
                if (uid != null) verificationRepository.myRequest(uid) else flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pendingRequests: StateFlow<List<VerificationRequestModel>> = requestsStale
        .cover("Verification", "zgloszen do rozpatrzenia") {
            userRepository.whileSignedIn(emptyList()) { verificationRepository.pendingRequests() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _submitError = MutableStateFlow<String?>(null)
    val submitError: StateFlow<String?> = _submitError.asStateFlow()

    fun clearSubmitError() {
        _submitError.value = null
    }

    suspend fun submit(compression: DocumentScanCompressionResult?, docType: String): Boolean {
        _submitError.value = null
        val user = when (val read = userRepository.readCurrentUser()) {
            is CurrentUserRead.Loaded -> read.user
            CurrentUserRead.SignedOut, CurrentUserRead.Absent -> return false
            is CurrentUserRead.Unavailable -> {
                Log.e("Verification", "Nie udalo sie odczytac profilu przed wyslaniem zgloszenia", read.error)
                _submitError.value = UserFacingError.message(read.error, UserFacingError.PROFILE_READ_FAILED)
                return false
            }
        }

        val imageBase64 = when (compression) {
            null -> null
            DocumentScanCompressionResult.UnreadableImage -> {
                _submitError.value = COMPRESSION_UNREADABLE_MESSAGE
                return false
            }
            DocumentScanCompressionResult.TooLarge -> {
                _submitError.value = COMPRESSION_TOO_LARGE_MESSAGE
                return false
            }
            is DocumentScanCompressionResult.Success -> compression.base64
        }

        if (imageBase64.isNullOrBlank()) {
            _submitError.value = "Najpierw wybierz zdjęcie dokumentu."
            return false
        }
        if (!fitsInDocument(imageBase64.length)) {
            _submitError.value = COMPRESSION_TOO_LARGE_MESSAGE
            return false
        }

        val request = VerificationRequestModel(
            uid = user.id,
            docImageBase64 = imageBase64,
            docType = docType,
            status = VerificationStatus.PENDING,
            submittedAt = clock()
        )

        return try {
            verificationRepository.submit(request)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Verification", "Nie udalo sie wyslac zgloszenia weryfikacyjnego", e)
            _submitError.value = "Nie udało się wysłać zgłoszenia. Spróbuj ponownie."
            false
        }
    }

    suspend fun decide(uid: String, approved: Boolean, note: String): Boolean {
        _submitError.value = null
        when (val read = userRepository.readCurrentUser()) {
            is CurrentUserRead.Loaded -> Unit
            CurrentUserRead.SignedOut, CurrentUserRead.Absent -> {
                _submitError.value = NOT_SIGNED_IN_MESSAGE
                return false
            }
            is CurrentUserRead.Unavailable -> {
                Log.e("Verification", "Nie udalo sie odczytac profilu przed decyzja weryfikacyjna", read.error)
                _submitError.value = UserFacingError.message(read.error, UserFacingError.PROFILE_READ_FAILED)
                return false
            }
        }

        if (!approved && note.isBlank()) {
            _submitError.value = "Podaj powód odrzucenia zgłoszenia."
            return false
        }

        return try {
            verificationRepository.decide(uid, approved, note)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Verification", "Nie udalo sie rozstrzygnac zgloszenia weryfikacyjnego", e)
            _submitError.value = UserFacingError.message(e, DECISION_FAILED_MESSAGE)
            false
        }
    }
}
