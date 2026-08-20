package com.example.stellare.data.remote

import com.example.stellare.data.model.UserModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreUserDataSource {
    private val db = Firebase.firestore

    suspend fun saveUser(user: UserModel) {
        db.collection("users").document(user.id).set(user).await()
    }

    fun getUser(uid: String): Flow<UserModel?> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(null)
            return@callbackFlow
        }
        val listener = db.collection("users").document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreUserDS", "Error fetching user $uid: ${error.message}")
                    // Don't close with error to prevent crash, just close the flow
                    close()
                    return@addSnapshotListener
                }
                trySend(snap?.toObject(UserModel::class.java))
            }
        awaitClose { listener.remove() }
    }

    fun getAllByRole(role: String): Flow<List<UserModel>> = callbackFlow {
        val listener = db.collection("users")
            .whereEqualTo("role", role)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreUserDS", "Error fetching users by role $role: ${error.message}")
                    // Don't close with error to prevent crash
                    close()
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(UserModel::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun getUserOnce(uid: String): UserModel? {
        if (uid.isEmpty()) return null
        return try {
            db.collection("users").document(uid).get().await()
                .toObject(UserModel::class.java)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreUserDS", "Error getting user once $uid", e)
            null
        }
    }

    fun getAllByRoleExcluding(role: String, excludeId: String): Flow<List<UserModel>> = callbackFlow {
        val listener = db.collection("users")
            .whereEqualTo("role", role)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreUserDS", "Error fetching users by role $role: ${error.message}")
                    // Don't close with error to prevent crash
                    close()
                    return@addSnapshotListener
                }
                val filtered = snap?.toObjects(UserModel::class.java)
                    ?.filter { it.id != excludeId }
                    ?: emptyList()
                trySend(filtered)
            }
        awaitClose { listener.remove() }
    }
}