package com.example.stellare.data.remote

import com.example.stellare.data.model.PrivateProfile
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestorePrivateProfileDataSource : PrivateProfileDataSource {
    private val db = Firebase.firestore

    private fun ref(uid: String): DocumentReference =
        db.collection(FirestoreCollections.USERS).document(uid)
            .collection(FirestoreCollections.PRIVATE).document(FirestoreCollections.CONTACT)

    override fun getPrivateProfile(uid: String): Flow<PrivateProfile?> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(null)
            return@callbackFlow
        }
        val listener = ref(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                android.util.Log.e("FirestorePrivateDS", "Error fetching private profile $uid: ${error.message}")
                close(error)
                return@addSnapshotListener
            }
            trySend(snap?.toObject(PrivateProfile::class.java))
        }
        awaitClose { listener.remove() }
    }

    /**
     * Reguła tworzenia wymaga pola `coinsBalance` wprost i równego 0: dokument utworzony bez niego
     * zostawia konto na stałe bez portfela, bo saldo jest wtedy nieosiągalne żadną drogą. Istniejącego
     * dokumentu nie wolno nadpisać, bo zapis zerowego salda nad niezerowym zniszczyłby monety.
     */
    override suspend fun createPrivateProfile(uid: String, phone: String, email: String) {
        val reference = ref(uid)
        db.runTransaction { transaction ->
            if (!transaction.get(reference).exists()) {
                transaction.set(reference, PrivateProfile(phone = phone, email = email, coinsBalance = 0))
            }
            null
        }.await()
    }

    override suspend fun savePhone(uid: String, phone: String) {
        ref(uid).update("phone", phone).await()
    }

    override suspend fun saveEmail(uid: String, email: String) {
        ref(uid).update("email", email).await()
    }

    override suspend fun deletePrivateProfile(uid: String) {
        ref(uid).delete().await()
    }
}
