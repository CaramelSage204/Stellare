package com.example.stellare.data.remote

import com.example.stellare.data.model.RatingModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

data class DocumentWrite(
    val collection: String,
    val id: String,
    val data: Any,
    val merge: Boolean = false
)

interface RatingTransaction {
    fun readRating(psychologistId: String): RatingModel
    fun write(writes: List<DocumentWrite>)
}

interface TransactionRunner {
    suspend fun <T> run(block: (RatingTransaction) -> T): T
}

class FirestoreTransactionRunner(
    private val db: Lazy<FirebaseFirestore> = lazy { Firebase.firestore }
) : TransactionRunner {
    override suspend fun <T> run(block: (RatingTransaction) -> T): T {
        val firestore = db.value
        return firestore.runTransaction { transaction ->
            block(FirestoreRatingTransaction(firestore, transaction))
        }.await()
    }
}

private class FirestoreRatingTransaction(
    private val db: FirebaseFirestore,
    private val transaction: Transaction
) : RatingTransaction {
    override fun readRating(psychologistId: String): RatingModel =
        transaction.get(db.collection(FirestoreCollections.RATINGS).document(psychologistId))
            .toObject(RatingModel::class.java) ?: RatingModel()

    override fun write(writes: List<DocumentWrite>) {
        writes.forEach { write ->
            val ref = db.collection(write.collection).document(write.id)
            if (write.merge) transaction.set(ref, write.data, SetOptions.merge()) else transaction.set(ref, write.data)
        }
    }
}
