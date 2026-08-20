package com.example.stellare.data.remote

import android.util.Log
import com.example.stellare.data.model.MessageModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreMessageDataSource {
    private val db = Firebase.firestore
    private val collection = db.collection("messages")

    suspend fun saveMessage(message: MessageModel) {
        val id = if (message.messageId.isEmpty()) {
            collection.document().id
        } else {
            message.messageId
        }
        val messageToSave = message.copy(messageId = id)
        collection.document(id).set(messageToSave).await()
    }

    fun getMessagesForChat(chatId: String): Flow<List<MessageModel>> = callbackFlow {
        val listener = collection
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e("FirestoreMessageDS", "getMessagesForChat failed for chatId=$chatId", error)
                }
                trySend(snap?.toObjects(MessageModel::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }
}