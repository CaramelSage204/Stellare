package com.example.stellare.data.remote

import com.example.stellare.data.model.WalletTransactionModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreWalletTransactionDataSource : WalletDataSource {
    private val db = Firebase.firestore
    private val collection = db.collection("wallet_transactions")

    override fun getTransactionsForUser(userId: String): Flow<List<WalletTransactionModel>> = callbackFlow {
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
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(WalletTransactionModel::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    override suspend fun saveTransaction(transaction: WalletTransactionModel) {
        val id = if (transaction.transactionId.isEmpty()) {
            collection.document().id
        } else {
            transaction.transactionId
        }
        val transactionToSave = transaction.copy(transactionId = id)
        collection.document(id).set(transactionToSave).await()
    }

    override suspend fun topUp(userId: String, amount: Int, title: String, timestamp: Long, entryId: String) {
        val contactRef = db.collection(FirestoreCollections.privateOf(userId)).document(FirestoreCollections.CONTACT)
        val plan = planTopUp(userId, amount, title, timestamp, entryId)
        val batch = db.batch()
        batch.update(contactRef, "coinsBalance", FieldValue.increment(plan.balanceIncrement))
        batch.set(collection.document(plan.entry.transactionId), plan.entry)
        batch.commit().await()
    }

    /**
     * Zapytanie po polach, nie get() po sciezce i nie filtr documentId(): regula odczytu porownuje
     * resource.data.userId, a dla dokumentu, ktorego nie ma, resource jest null i oba kończą się
     * odmową zamiast "brak" (zmierzone w wallet-reconciliation.test.js). Regula tworzenia wymusza
     * transactionId == id dokumentu, a ksiega jest niezmienna, wiec pole zastepuje identyfikator.
     * Zrodlo SERVER: pamiec podreczna zawiera lokalnie zakolejkowany zapis i potwierdzilaby
     * wpis, ktorego serwer nigdy nie przyjal.
     */
    override suspend fun topUpLanded(userId: String, entryId: String): Boolean =
        !collection
            .whereEqualTo(WalletLedgerFields.USER_ID, userId)
            .whereEqualTo(WalletLedgerFields.TRANSACTION_ID, entryId)
            .limit(1)
            .get(Source.SERVER)
            .await()
            .isEmpty
}
