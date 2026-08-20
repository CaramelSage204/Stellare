package com.example.stellare.data.remote

import com.example.stellare.data.model.FavoriteModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreFavoriteDataSource {
    private val db = Firebase.firestore
    private val collection = db.collection("favorites")

    fun getFavoritesForUser(userId: String): Flow<List<FavoriteModel>> = callbackFlow {
        val listener = collection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.toObjects(FavoriteModel::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveFavorite(favorite: FavoriteModel) {
        // Use a composite ID to prevent duplicates and make deletion easier
        val docId = "${favorite.userId}_${favorite.psychologistId}"
        collection.document(docId).set(favorite).await()
    }

    suspend fun deleteFavorite(userId: String, psychologistId: String) {
        val docId = "${userId}_${psychologistId}"
        collection.document(docId).delete().await()
    }
}
