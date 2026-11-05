package com.example.stellare.data.remote

import com.example.stellare.data.model.PostModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestorePostDataSource : PostDataSource {
    private val db = Firebase.firestore
    private val collection = db.collection("posts")

    override fun getActivePosts(type: String): Flow<List<PostModel>> = callbackFlow {
        val listener = collection
            .whereEqualTo("isActive", true)
            .whereEqualTo("type", type)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestorePostDS", "Error fetching active posts of type $type: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                trySend(postsOf(snap))
            }
        awaitClose { listener.remove() }
    }

    override fun getPostsByAuthor(authorId: String): Flow<List<PostModel>> = callbackFlow {
        if (authorId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = collection
            .whereEqualTo("authorId", authorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestorePostDS", "Error fetching posts for author $authorId: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                trySend(postsOf(snap))
            }
        awaitClose { listener.remove() }
    }

    // Tozsamosc ogloszenia pochodzi z identyfikatora dokumentu, nigdy z pola "id".
    // Pole pisze klient, wiec dwa dokumenty moga twierdzic, ze sa tym samym
    // ogloszeniem; wtedy savePost() i deletePost() celuja w posts/<pole>, czyli w
    // dokument, ktory nie istnieje. Reguly odmawiaja, odrzucona mutacja zostaje w
    // lokalnej kolejce urzadzenia i jest ponawiana przy kazdym starcie - zadanie 11
    // zmierzylo na urzadzeniu petle awarii, z ktorej wyjsciem bylo wyczyszczenie
    // danych aplikacji. Po tym mapowaniu duplikat klucza jest niemozliwy z konstrukcji.
    private fun postsOf(snapshot: QuerySnapshot?): List<PostModel> =
        snapshot?.documents?.mapNotNull { document ->
            document.toObject(PostModel::class.java)?.copy(id = document.id)
        } ?: emptyList()

    override suspend fun savePost(post: PostModel) {
        require(post.id.isNotEmpty()) { "Identyfikator ogłoszenia nie może być pusty." }
        collection.document(post.id).set(post).await()
    }

    override suspend fun deactivatePost(postId: String) {
        collection.document(postId).update("isActive", false).await()
    }

    override suspend fun deletePost(postId: String) {
        collection.document(postId).delete().await()
    }

    override fun newPostId(): String = collection.document().id
}
