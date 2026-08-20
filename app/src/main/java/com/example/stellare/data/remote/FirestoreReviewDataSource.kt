package com.example.stellare.data.remote

import com.example.stellare.data.model.ReviewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreReviewDataSource {
    private val db = Firebase.firestore
    private val collection = db.collection("reviews")

    suspend fun saveReview(review: ReviewModel) {
        val id = if (review.reviewId.isEmpty()) {
            collection.document().id
        } else {
            review.reviewId
        }
        val reviewToSave = review.copy(reviewId = id)
        collection.document(id).set(reviewToSave).await()
    }

    fun getReviewsForPsychologist(psychologistId: String): Flow<List<ReviewModel>> = callbackFlow {
        if (psychologistId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = collection
            .whereEqualTo("psychologistId", psychologistId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreReviewDS", "Error fetching reviews for psych $psychologistId: ${error.message}")
                    close()
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(ReviewModel::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }
}
