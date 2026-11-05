package com.example.stellare.data.remote

import com.example.stellare.data.model.FavoriteModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreFavoriteDataSource : FavoriteDataSource {
    private val db = Firebase.firestore
    private val collection = db.collection("favorites")

    override fun getFavoritesForUser(userId: String): Flow<List<FavoriteModel>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = collection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreFavoriteDS", "Error fetching favorites for user $userId: ${error.message}")
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(FavoriteModel::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    override suspend fun saveFavorite(favorite: FavoriteModel) {
        collection.document(compositeId(favorite.userId, favorite.psychologistId)).set(favorite).await()
    }

    override suspend fun deleteFavorite(userId: String, psychologistId: String) {
        collection.document(compositeId(userId, psychologistId)).delete().await()
    }

    private fun compositeId(userId: String, psychologistId: String): String = "${userId}_${psychologistId}"
}
