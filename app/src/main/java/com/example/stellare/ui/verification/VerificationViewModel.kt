package com.example.stellare.ui.verification

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.VerificationRequestModel
import com.example.stellare.data.model.VerificationStatus
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.VerificationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationViewModel(
    private val verificationRepository: VerificationRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val myRequest: StateFlow<VerificationRequestModel?> = currentUser
        .flatMapLatest { user ->
            if (user != null) verificationRepository.myRequest(user.id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pendingRequests: StateFlow<List<VerificationRequestModel>> = currentUser
        .flatMapLatest { user ->
            if (user != null) verificationRepository.pendingRequests() else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _submitError = MutableStateFlow<String?>(null)
    val submitError: StateFlow<String?> = _submitError.asStateFlow()

    fun clearSubmitError() {
        _submitError.value = null
    }

    suspend fun submit(compression: DocumentScanCompressionResult?, docType: String): Boolean {
        _submitError.value = null
        val user = userRepository.getCurrentUser() ?: return false

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
            submittedAt = System.currentTimeMillis()
        )

        return try {
            verificationRepository.submit(request)
            true
        } catch (e: Exception) {
            Log.e("Verification", "Nie udalo sie wyslac zgloszenia weryfikacyjnego", e)
            _submitError.value = "Nie udało się wysłać zgłoszenia. Spróbuj ponownie."
            false
        }
    }

    suspend fun decide(uid: String, approved: Boolean, note: String): Boolean {
        _submitError.value = null
        userRepository.getCurrentUser() ?: return false

        if (!approved && note.isBlank()) {
            _submitError.value = "Podaj powód odrzucenia zgłoszenia."
            return false
        }

        return try {
            verificationRepository.decide(uid, approved, note)
            true
        } catch (e: Exception) {
            Log.e("Verification", "Nie udalo sie rozstrzygnac zgloszenia weryfikacyjnego", e)
            false
        }
    }
}
