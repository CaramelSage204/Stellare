package com.example.stellare.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirebaseAuthDataSource : AuthDataSource {

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

    override suspend fun signInWithGoogle(idToken: String): String? {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
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

    override fun signOut() {
        auth.signOut()
    }
}