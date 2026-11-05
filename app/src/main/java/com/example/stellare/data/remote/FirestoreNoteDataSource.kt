package com.example.stellare.data.remote

import com.example.stellare.data.model.NoteModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreNoteDataSource : NoteDataSource {
    private val db = Firebase.firestore
    private val collection = db.collection("notes")

    override suspend fun saveNote(note: NoteModel) {
        val id = if (note.noteId.isEmpty()) {
            collection.document().id
        } else {
            note.noteId
        }
        val noteToSave = note.copy(noteId = id)
        collection.document(id).set(noteToSave).await()
    }

    override fun getNotesForChat(chatId: String): Flow<List<NoteModel>> = callbackFlow {
        if (chatId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = collection
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreNoteDS", "Error fetching notes for chat $chatId: ${error.message}")
                    return@addSnapshotListener
                }
                trySend(notesOf(snap))
            }
        awaitClose { listener.remove() }
    }

    // Tozsamosc notatki pochodzi z identyfikatora dokumentu, nigdy z pola "noteId":
    // deleteNote() celuje w notes/<pole>, a jeden z krokow usuwania konta
    // (AccountDeletion.DeleteNote) wlasnie tak kasuje notatki. Pole pisze klient, wiec
    // bez tego mapowania jeden rozjechany dokument przerywa usuwanie konta w polowie.
    private fun notesOf(snapshot: QuerySnapshot?): List<NoteModel> =
        snapshot?.documents?.mapNotNull { document ->
            document.toObject(NoteModel::class.java)?.copy(noteId = document.id)
        } ?: emptyList()

    override suspend fun deleteNote(noteId: String) {
        collection.document(noteId).delete().await()
    }
}
