package com.example.stellare.data.remote

import android.util.Log
import com.example.stellare.data.model.MessageModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreMessageDataSource : MessageDataSource {
    private val db = Firebase.firestore
    private val collection = db.collection("messages")

    override suspend fun saveMessage(message: MessageModel) {
        val id = if (message.messageId.isEmpty()) {
            collection.document().id
        } else {
            message.messageId
        }
        val messageToSave = message.copy(messageId = id)
        collection.document(id).set(messageToSave).await()
    }

    override fun getMessagesForChat(chatId: String): Flow<List<MessageModel>> = callbackFlow {
        if (chatId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = collection
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e("FirestoreMessageDS", "getMessagesForChat failed for chatId=$chatId", error)
                    return@addSnapshotListener
                }
                trySend(messagesOf(snap))
            }
        awaitClose { listener.remove() }
    }

    // Tozsamosc wiadomosci pochodzi z identyfikatora dokumentu, nigdy z pola
    // "messageId". Pole pisze klient, wiec dwie wiadomosci moga twierdzic, ze sa
    // ta sama wiadomoscia - a messageId jest kluczem listy na ekranie rozmowy.
    private fun messagesOf(snapshot: QuerySnapshot?): List<MessageModel> =
        snapshot?.documents?.mapNotNull { document ->
            document.toObject(MessageModel::class.java)?.copy(messageId = document.id)
        } ?: emptyList()
}