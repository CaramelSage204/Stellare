package com.example.stellare.data.remote

import com.example.stellare.data.model.NoteModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreNoteDataSource {
    private val db = Firebase.firestore
    private val collection = db.collection("notes")

    suspend fun saveNote(note: NoteModel) {
        val id = if (note.noteId.isEmpty()) {
            collection.document().id
        } else {
            note.noteId
        }
        val noteToSave = note.copy(noteId = id)
        collection.document(id).set(noteToSave).await()
    }

    fun getNotesForChat(chatId: String): Flow<List<NoteModel>> = callbackFlow {
        val listener = collection
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.toObjects(NoteModel::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun deleteNote(noteId: String) {
        collection.document(noteId).delete().await()
    }
}
