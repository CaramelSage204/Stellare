package com.example.stellare.ui.auth

import android.content.Context
import android.util.Log
import androidx.credentials.Credential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserMessageException
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.ui.UserFacingError
import com.example.stellare.ui.session.ProfileCompletion
import com.example.stellare.ui.session.RegistrationRules
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val FIREBASE_WEB_CLIENT_ID = "818324884549-c7i2ohrnv3ksb3f9u2jhtqd4spq97lim.apps.googleusercontent.com"

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModel(
    private val userRepository: UserRepository,
    private val authDataSource: AuthDataSource,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    val authStatus: StateFlow<AuthStatus> = channelFlow {
        var hasSourceResponded = false
        val unknownStatusTimeout = launch {
            delay(UNKNOWN_STATUS_TIMEOUT_MS)
            if (!hasSourceResponded) {
                send(AuthStatus.LOGGED_OUT)
            }
        }
        signedInUserWithDocument().collect { (uid, read) ->
            hasSourceResponded = true
            unknownStatusTimeout.cancel()
            if (uid != null && read is CurrentUserRead.Loaded) ensurePrivateProfile(uid)
            send(ProfileCompletion.status(uid, read))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthStatus.UNKNOWN)

    private fun ensurePrivateProfile(uid: String) {
        viewModelScope.launch {
            try {
                userRepository.ensurePrivateProfile(uid, authDataSource.getCurrentUserEmailOnce().orEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Auth", "Zakladanie brakujacego dokumentu prywatnego nie powiodlo sie", e)
            }
        }
    }

    fun suggestedName(): Pair<String, String> =
        ProfileCompletion.splitDisplayName(authDataSource.getCurrentUserDisplayNameOnce())

    private fun signedInUserWithDocument(): Flow<Pair<String?, CurrentUserRead>> =
        userRepository.signedInUserIdFlow().flatMapLatest { uid ->
            if (uid == null) flowOf(null to CurrentUserRead.SignedOut)
            else userRepository.getCurrentUserFlow().map { read -> uid to read }
        }

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _showTutorialPrompt = MutableStateFlow(false)
    val showTutorialPrompt: StateFlow<Boolean> = _showTutorialPrompt.asStateFlow()

    private val _isSavingProfile = MutableStateFlow(false)
    val isSavingProfile: StateFlow<Boolean> = _isSavingProfile.asStateFlow()

    fun completeProfile(
        role: UserRole,
        firstName: String,
        lastName: String,
        ageText: String,
        gender: String,
        bio: String,
        priceText: String
    ) {
        if (_isSavingProfile.value) return
        val blocked = ProfileCompletion.blockMessage(role, firstName, lastName, ageText, priceText)
        if (blocked != null) {
            _authError.value = blocked
            return
        }
        viewModelScope.launch {
            _authError.value = null
            _isSavingProfile.value = true
            try {
                userRepository.whileRegistering {
                    val uid = authDataSource.getCurrentUserIdOnce()
                        ?: throw UserMessageException("Sesja wygasła. Zaloguj się ponownie.")
                    userRepository.registerProfile(
                        user = UserModel(
                            id = uid,
                            firstName = firstName.trim(),
                            lastName = lastName.trim(),
                            age = RegistrationRules.parseAge(ageText) ?: 0,
                            gender = gender,
                            role = role,
                            pricePerSession = ProfileCompletion.priceFor(role, priceText),
                            bio = bio,
                            isCurrentUser = true
                        ),
                        phone = "",
                        email = authDataSource.getCurrentUserEmailOnce().orEmpty()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Auth", "Profile completion failed", e)
                _authError.value = UserFacingError.message(e, "Nie udało się zapisać profilu. Spróbuj ponownie.")
            } finally {
                _isSavingProfile.value = false
            }
        }
    }

    private val passwordResetController = PasswordResetController(viewModelScope, clock, authDataSource)
    val passwordReset: StateFlow<PasswordResetState> = passwordResetController.state
    val resetCooldownSeconds: StateFlow<Int> = passwordResetController.cooldownSeconds

    fun sendPasswordReset(email: String) = passwordResetController.send(email)

    fun resetPasswordResetState() = passwordResetController.reset()

    private val phoneController = PhoneVerificationController(viewModelScope, clock, authDataSource, userRepository)
    val phoneVerification: StateFlow<PhoneVerificationState> = phoneController.state
    val phoneResendCooldownSeconds: StateFlow<Int> = phoneController.resendCooldownSeconds
    val phoneVerificationOffer: StateFlow<String?> = phoneController.offer

    fun openPhoneVerification() = phoneController.open()

    fun requestPhoneCode(input: String) = phoneController.requestCode(input)

    fun confirmPhoneCode(code: String) = phoneController.confirmCode(code)

    fun changePhoneNumber() = phoneController.changeNumber()

    fun retrySavingPhoneToProfile() = phoneController.retrySavingToProfile()

    fun closePhoneVerification() = phoneController.close()

    fun takePhoneVerificationOffer(): String? = phoneController.takeOffer()

    fun dismissPhoneVerificationOffer() = phoneController.dismissOffer()

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
        val identityError = RegistrationRules.identityError(firstName, lastName, age.toString())
        if (identityError != null) {
            _authError.value = identityError
            return
        }
        viewModelScope.launch {
            _authError.value = null
            try {
                userRepository.whileRegistering {
                    val uid = authDataSource.registerWithEmail(email, password)
                        ?: throw Exception("Nie udało się utworzyć konta.")
                    val user = UserModel(
                        id = uid,
                        firstName = firstName,
                        lastName = lastName,
                        age = age,
                        gender = gender,
                        role = role,
                        qualifications = qualifications,
                        specializations = specializations,
                        pricePerSession = pricePerSession,
                        bio = bio,
                        isCurrentUser = true,
                        customPrices = customPrices
                    )
                    userRepository.registerProfile(user, phone = "", email = email)
                }
                _showTutorialPrompt.value = true
                PhoneNumber.normalize(phone)?.let { phoneController.offerVerification(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Auth", "Registration failed", e)
                _authError.value = "Błąd rejestracji: ${UserFacingError.authMessage(e, "nie udało się utworzyć konta. Spróbuj ponownie.")}"
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Auth", "Login failed", e)
                _authError.value = "Błąd logowania: ${UserFacingError.authMessage(e, "nie udało się zalogować. Spróbuj ponownie.")}"
            }
        }
    }

    fun signInWithGoogle(credential: Credential) {
        viewModelScope.launch {
            _authError.value = null
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                authDataSource.signInWithGoogle(googleIdTokenCredential.idToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Auth", "Google sign-in failed", e)
                _authError.value = "Błąd logowania Google: ${UserFacingError.authMessage(e, "nie udało się zalogować. Spróbuj ponownie.")}"
            }
        }
    }

    private var facebookSignIn: Job? = null

    fun signInWithFacebook() {
        if (facebookSignIn?.isActive == true) return
        facebookSignIn = viewModelScope.launch {
            _authError.value = null
            try {
                val uid = authDataSource.signInWithFacebook()
                if (uid == null) _authError.value = facebookFailureMessage(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Auth", "Facebook sign-in failed", e)
                _authError.value = facebookFailureMessage(e)
            }
        }
    }

    private fun facebookFailureMessage(error: Throwable?): String {
        val fallback = "nie udało się zalogować. Spróbuj ponownie."
        val text = if (error == null) fallback else UserFacingError.authMessage(error, fallback)
        return "Błąd logowania Facebook: $text"
    }

    fun onGoogleCredentialFailure(error: Throwable) {
        Log.e("Auth", "Google credential retrieval failed", error)
        _authError.value = "Błąd logowania Google: ${UserFacingError.authMessage(error, "nie udało się zalogować. Spróbuj ponownie.")}"
    }

    fun buildGoogleSignInRequest(context: Context): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(FIREBASE_WEB_CLIENT_ID)
            .build()
        return GetCredentialRequest(
            credentialOptions = listOf(googleIdOption)
        )
    }

    fun logout() {
        phoneController.reset()
        phoneController.dismissOffer()
        viewModelScope.launch {
            authDataSource.signOut()
        }
    }

    private companion object {
        const val UNKNOWN_STATUS_TIMEOUT_MS = 5_000L
    }
}
