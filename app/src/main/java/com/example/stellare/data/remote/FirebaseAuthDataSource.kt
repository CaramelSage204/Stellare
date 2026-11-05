package com.example.stellare.data.remote

import android.app.Activity
import com.example.stellare.CurrentActivity
import com.example.stellare.data.model.RecentLoginRequiredException
import com.example.stellare.data.model.UserMessageException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseAuthDataSource(
    private val activityProvider: () -> Activity? = CurrentActivity::get
) : AuthDataSource {

    private val auth = FirebaseAuth.getInstance()

    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override val currentUserId: Flow<String?> = currentUser.map { it?.uid }

    fun getCurrentUserOnce(): FirebaseUser? = auth.currentUser

    override fun getCurrentUserIdOnce(): String? = getCurrentUserOnce()?.uid

    override fun getCurrentUserEmailOnce(): String? = getCurrentUserOnce()?.email

    override fun getCurrentUserDisplayNameOnce(): String? = getCurrentUserOnce()?.displayName

    override fun getCurrentUserPhoneNumberOnce(): String? =
        getCurrentUserOnce()?.phoneNumber?.takeIf { it.isNotBlank() }

    override suspend fun signInWithGoogle(idToken: String): String? {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        return result.user?.uid
    }

    override suspend fun signInWithFacebook(): String? {
        val activity = activityProvider() ?: return null
        val provider = OAuthProvider.newBuilder("facebook.com")
            .setScopes(listOf("email", "public_profile"))
            .build()
        val result = auth.startActivityForSignInWithProvider(activity, provider).await()
        return result.user?.uid
    }

    override suspend fun signInWithEmail(email: String, password: String): String? {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user?.uid
    }

    override suspend fun registerWithEmail(email: String, password: String): String? {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user?.uid
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        auth.setLanguageCode("pl")
        auth.sendPasswordResetEmail(email).await()
    }

    override suspend fun requestPhoneCode(phoneNumber: String): PhoneCodeRequest? {
        val activity = activityProvider() ?: return null
        auth.setLanguageCode("pl")
        return when (val outcome = awaitVerificationOutcome(phoneNumber, activity)) {
            is VerificationOutcome.CodeSent -> PhoneCodeRequest.CodeSent(outcome.verificationId)
            is VerificationOutcome.Instant -> PhoneCodeRequest.AlreadyLinked(linkPhoneCredential(outcome.credential))
        }
    }

    override suspend fun confirmPhoneCode(verificationId: String, code: String): String =
        linkPhoneCredential(PhoneAuthProvider.getCredential(verificationId, code))

    override suspend fun lastSignInMillis(): Long? {
        val user = auth.currentUser ?: return null
        val seconds = user.getIdToken(false).await().claims[AUTH_TIME_CLAIM] as? Number
        return seconds?.toLong()?.times(MILLIS_PER_SECOND)
    }

    override suspend fun deleteCurrentAccount() {
        val user = auth.currentUser ?: throw UserMessageException(SESSION_LOST)
        try {
            user.delete().await()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            throw RecentLoginRequiredException()
        }
        auth.signOut()
    }

    override fun signOut() {
        auth.signOut()
    }

    private suspend fun linkPhoneCredential(credential: PhoneAuthCredential): String {
        val user = auth.currentUser ?: throw UserMessageException(SESSION_LOST)
        user.linkWithCredential(credential).await()
        val claims = user.getIdToken(true).await().claims
        return claims[PHONE_NUMBER_CLAIM] as? String ?: throw UserMessageException(NUMBER_NOT_IN_TOKEN)
    }

    private suspend fun awaitVerificationOutcome(phoneNumber: String, activity: Activity): VerificationOutcome =
        suspendCancellableCoroutine { continuation ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    if (continuation.isActive) continuation.resume(VerificationOutcome.Instant(credential))
                }

                override fun onVerificationFailed(error: FirebaseException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    if (continuation.isActive) continuation.resume(VerificationOutcome.CodeSent(verificationId))
                }
            }
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(VERIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }

    private sealed interface VerificationOutcome {
        data class CodeSent(val verificationId: String) : VerificationOutcome
        data class Instant(val credential: PhoneAuthCredential) : VerificationOutcome
    }

    private companion object {
        const val PHONE_NUMBER_CLAIM = "phone_number"
        const val AUTH_TIME_CLAIM = "auth_time"
        const val MILLIS_PER_SECOND = 1000L
        const val VERIFICATION_TIMEOUT_SECONDS = 60L
        const val SESSION_LOST = "Sesja wygasła. Zaloguj się ponownie."
        const val NUMBER_NOT_IN_TOKEN = "Konto nie potwierdziło numeru. Spróbuj ponownie."
    }
}
