package com.example.stellare.data.remote

import com.example.stellare.data.model.ChatModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreChatDataSource {
    private val db = Firebase.firestore
    private val collection = db.collection("chats")

    suspend fun saveChat(chat: ChatModel) {
        val id = if (chat.chatId.isEmpty()) {
            collection.document().id
        } else {
            chat.chatId
        }
        val chatToSave = chat.copy(chatId = id)
        collection.document(id).set(chatToSave).await()
    }

    fun getChatsForUser(userId: String): Flow<List<ChatModel>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = collection
            .whereArrayContains("participants", userId)
            .orderBy("lastMessageTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreChat", "Error fetching chats for user $userId: ${error.message}", error)
                    close() // Close the flow without an error to avoid crashing the app
                    return@addSnapshotListener
                }
                val chats = snap?.toObjects(ChatModel::class.java) ?: emptyList()
                trySend(chats)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getChatByParticipants(psychId: String, patientId: String): ChatModel? {
        return try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
            val searchId = if (currentUserId == psychId) psychId else patientId
            val snap = collection
                .whereArrayContains("participants", searchId)
                .get()
                .await()
            snap.toObjects(ChatModel::class.java).firstOrNull {
                it.participants.contains(psychId) && it.participants.contains(patientId)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreChat", "Error getting chat by participants", e)
            null
        }
    }


    suspend fun updateChat(chat: ChatModel) {
        collection.document(chat.chatId).set(chat).await()
    }
}
