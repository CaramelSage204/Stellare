package com.example.stellare.data.remote

import com.example.stellare.data.model.CompletionModel
import com.example.stellare.data.model.ReviewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

fun reviewWithRatingWrites(
    review: ReviewModel,
    psychologistId: String,
    newRatingSum: Int,
    newRatingCount: Int
): List<DocumentWrite> = listOf(
    DocumentWrite(FirestoreCollections.REVIEWS, review.reviewId, review),
    DocumentWrite(FirestoreCollections.RATINGS, psychologistId, mapOf("sum" to newRatingSum, "count" to newRatingCount), merge = true)
)

fun submitReviewWithRating(transaction: RatingTransaction, review: ReviewModel, psychologistId: String) {
    val updated = transaction.readRating(psychologistId).plus(review.rating)
    transaction.write(reviewWithRatingWrites(review, psychologistId, updated.sum, updated.count))
}

class FirestoreReviewDataSource(
    private val runner: TransactionRunner = FirestoreTransactionRunner()
) : ReviewDataSource {
    private val db by lazy { Firebase.firestore }
    private val collection by lazy { db.collection(FirestoreCollections.REVIEWS) }

    override suspend fun saveReviewWithRating(review: ReviewModel, psychologistId: String) {
        runner.run { transaction -> submitReviewWithRating(transaction, review, psychologistId) }
    }

    override suspend fun hasCompletedSession(psychologistId: String, patientId: String): Boolean =
        db.collection("completions")
            .document(CompletionModel.idFor(psychologistId, patientId))
            .get().await().exists()

    override fun getReviewsForPsychologist(psychologistId: String): Flow<List<ReviewModel>> = callbackFlow {
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
                    close(error)
                    return@addSnapshotListener
                }
                trySend(reviewsOf(snap))
            }
        awaitClose { listener.remove() }
    }

    // Tozsamosc recenzji pochodzi z identyfikatora dokumentu, nigdy z pola "reviewId".
    // Dzis cel zapisu liczy sie z pary psycholog_recenzent, nie z odczytanego pola, wiec
    // to obrona w glab: pole pisane przez klienta nie moze byc tozsamoscia obiektu, bo
    // kazdy przyszly konsument reviewId wziąłby ja za prawde.
    private fun reviewsOf(snapshot: QuerySnapshot?): List<ReviewModel> =
        snapshot?.documents?.mapNotNull { document ->
            document.toObject(ReviewModel::class.java)?.copy(reviewId = document.id)
        } ?: emptyList()
}
