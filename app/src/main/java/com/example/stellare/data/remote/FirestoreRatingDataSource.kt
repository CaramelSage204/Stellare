package com.example.stellare.data.remote

import com.example.stellare.data.model.RatingModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreRatingDataSource : RatingDataSource {
    private val collection = Firebase.firestore.collection(FirestoreCollections.RATINGS)

    override fun getRatings(): Flow<Map<String, RatingModel>> = callbackFlow {
        val listener = collection.addSnapshotListener { snap, error ->
            if (error != null) {
                android.util.Log.e("FirestoreRatingDS", "Error fetching ratings: ${error.message}")
                close(error)
                return@addSnapshotListener
            }
            trySend(
                snap?.documents
                    ?.mapNotNull { doc -> doc.toObject(RatingModel::class.java)?.let { doc.id to it } }
                    ?.toMap()
                    ?: emptyMap()
            )
        }
        awaitClose { listener.remove() }
    }

    override fun getRating(psychologistId: String): Flow<RatingModel> = callbackFlow {
        if (psychologistId.isEmpty()) {
            trySend(RatingModel())
            return@callbackFlow
        }
        val listener = collection.document(psychologistId).addSnapshotListener { snap, error ->
            if (error != null) {
                android.util.Log.e("FirestoreRatingDS", "Error fetching rating $psychologistId: ${error.message}")
                close(error)
                return@addSnapshotListener
            }
            trySend(snap?.toObject(RatingModel::class.java) ?: RatingModel())
        }
        awaitClose { listener.remove() }
    }
}
