package com.example.stellare.data.remote

import com.example.stellare.data.model.VerificationRequestModel
import com.example.stellare.data.model.VerificationStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreVerificationDataSource : VerificationDataSource {
    private val db = Firebase.firestore
    private val collection = db.collection("verificationRequests")
    private val usersCollection = db.collection("users")

    override fun myRequest(uid: String): Flow<VerificationRequestModel?> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(null)
            return@callbackFlow
        }
        val listener = collection.document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreVerificationDS", "Error fetching request for $uid: ${error.message}")
                    return@addSnapshotListener
                }
                trySend(snap?.toObject(VerificationRequestModel::class.java)?.copy(uid = uid))
            }
        awaitClose { listener.remove() }
    }

    override fun pendingRequests(): Flow<List<VerificationRequestModel>> = callbackFlow {
        val listener = collection
            .whereEqualTo("status", VerificationStatus.PENDING.name)
            .orderBy("submittedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreVerificationDS", "Error fetching pending requests: ${error.message}")
                    return@addSnapshotListener
                }
                val requests = snap?.documents?.mapNotNull { document ->
                    document.toObject(VerificationRequestModel::class.java)?.copy(uid = document.id)
                } ?: emptyList()
                trySend(requests)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun submit(request: VerificationRequestModel) {
        require(request.uid.isNotEmpty()) { "Identyfikator zgłaszającego nie może być pusty." }
        collection.document(request.uid).set(request).await()
    }

    // Zatwierdzenie to jeden zapis, nie dwa: status zgłoszenia i odznaka
    // użytkownika ida w tym samym batch() (ten sam wzorzec co
    // FirestoreReviewDataSource.saveReviewWithRating). Osobne zapisy zostawiają
    // przy awarii konto zweryfikowane bez zgłoszenia albo odwrotnie.
    override suspend fun decide(uid: String, approved: Boolean, note: String) {
        val status = if (approved) VerificationStatus.APPROVED else VerificationStatus.REJECTED
        val reviewerId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        val batch = db.batch()
        batch.update(
            collection.document(uid),
            mapOf(
                "status" to status.name,
                "reviewedBy" to reviewerId,
                "reviewedAt" to System.currentTimeMillis(),
                "reviewNote" to note
            )
        )
        if (approved) {
            batch.update(usersCollection.document(uid), mapOf("isVerified" to true))
        }
        batch.commit().await()
    }
}
