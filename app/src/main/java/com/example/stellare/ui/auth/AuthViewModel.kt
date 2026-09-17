package com.example.stellare.ui.auth

import android.content.Context
import android.util.Log
import androidx.credentials.Credential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.repository.UserRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModel(
    private val userRepository: UserRepository,
    private val authDataSource: AuthDataSource
) : ViewModel() {

    val authStatus: StateFlow<AuthStatus> = channelFlow {
        var hasSourceResponded = false
        val unknownStatusTimeout = launch {
            delay(UNKNOWN_STATUS_TIMEOUT_MS)
            if (!hasSourceResponded) {
                send(AuthStatus.LOGGED_OUT)
            }
        }
        userRepository.getCurrentUserFlow().collect { user ->
            hasSourceResponded = true
            unknownStatusTimeout.cancel()
            send(if (user != null) AuthStatus.LOGGED_IN else AuthStatus.LOGGED_OUT)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthStatus.UNKNOWN)

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _showTutorialPrompt = MutableStateFlow(false)
    val showTutorialPrompt: StateFlow<Boolean> = _showTutorialPrompt.asStateFlow()

    fun clearAuthError() {
        _authError.value = null
    }

    fun dismissTutorialPrompt() {
        _showTutorialPrompt.value = false
    }

    fun register(
        firstName: String,
        lastName: String,
        age: Int,
        gender: String,
        phone: String,
        email: String,
        password: String,
        role: UserRole,
        qualifications: String = "",
        specializations: String = "",
        pricePerSession: Double = 0.0,
        bio: String = "",
        customPrices: String = ""
    ) {
        viewModelScope.launch {
            _authError.value = null
            try {
                val uid = authDataSource.registerWithEmail(email, password)
                    ?: throw Exception("Nie udało się utworzyć konta.")
                val user = UserModel(
                    id = uid,
                    firstName = firstName,
                    lastName = lastName,
                    age = age,
                    gender = gender,
                    phone = phone,
                    email = email,
                    role = role,
                    qualifications = qualifications,
                    specializations = specializations,
                    pricePerSession = pricePerSession,
                    bio = bio,
                    isCurrentUser = true,
                    customPrices = customPrices
                )
                userRepository.insertUser(user)
                _showTutorialPrompt.value = true
            } catch (e: Exception) {
                Log.e("Auth", "Registration failed", e)
                _authError.value = "Błąd rejestracji: ${e.localizedMessage}"
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authError.value = null
            try {
                val uid = authDataSource.signInWithEmail(email, password)
                if (uid != null) {
                    val user = userRepository.getUserById(uid)
                    if (user == null) {
                        _authError.value = "Nie znaleziono danych użytkownika w bazie."
                    }
                } else {
                    _authError.value = "Błędny e-mail lub hasło."
                }
            } catch (e: Exception) {
                Log.e("Auth", "Login failed", e)
                _authError.value = "Błąd logowania: ${e.localizedMessage}"
            }
        }
    }

    fun signInWithGoogle(credential: Credential) {
        viewModelScope.launch {
            _authError.value = null
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                authDataSource.signInWithGoogle(googleIdTokenCredential.idToken)
            } catch (e: Exception) {
                Log.e("Auth", "Google sign-in failed", e)
                _authError.value = "Błąd logowania Google: ${e.localizedMessage}"
            }
        }
    }

    fun buildGoogleSignInRequest(context: Context): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId("818324884549-c7i2ohrnv3ksb3f9u2jhtqd4spq97lim.apps.googleusercontent.com") // from Firebase Console
            .build()
        return GetCredentialRequest(
            credentialOptions = listOf(googleIdOption)
        )
    }

    fun logout() {
        viewModelScope.launch {
            authDataSource.signOut()
        }
    }

    private companion object {
        const val UNKNOWN_STATUS_TIMEOUT_MS = 5_000L
    }
}
