package com.example.stellare.data.remote

import com.example.stellare.data.model.WalletTransactionModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreWalletTransactionDataSource {
    private val db = Firebase.firestore
    private val collection = db.collection("wallet_transactions")

    fun getTransactionsForUser(userId: String): Flow<List<WalletTransactionModel>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = collection
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreWalletDS", "Error fetching transactions for user $userId: ${error.message}")
                    close()
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(WalletTransactionModel::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveTransaction(transaction: WalletTransactionModel) {
        val id = if (transaction.transactionId.isEmpty()) {
            collection.document().id
        } else {
            transaction.transactionId
        }
        val transactionToSave = transaction.copy(transactionId = id)
        collection.document(id).set(transactionToSave).await()
    }
}
