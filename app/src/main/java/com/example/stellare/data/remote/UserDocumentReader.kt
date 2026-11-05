package com.example.stellare.data.remote

import com.example.stellare.data.model.UserModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

/**
 * Jednorazowy odczyt dokumentu użytkownika wydzielony za interfejs, żeby polityka błędu
 * w [FirestoreUserDataSource.getUserOnce] dała się sprawdzić testem: bez tego szwu przywrócenie
 * połykania wyjątku nie zabija żadnego testu, bo klasy ze gorącym `Firebase.firestore`
 * nie da się w ogóle skonstruować na maszynie wirtualnej.
 */
interface UserDocumentReader {
    suspend fun read(uid: String): UserModel?
}

class FirestoreUserDocumentReader(
    private val db: Lazy<FirebaseFirestore> = lazy { Firebase.firestore }
) : UserDocumentReader {
    override suspend fun read(uid: String): UserModel? =
        db.value.collection(FirestoreCollections.USERS).document(uid).get().await()
            .toObject(UserModel::class.java)?.copy(id = uid)
}
