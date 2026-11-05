package com.example.stellare.data.remote

import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.UserModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreUserDataSource(
    private val documents: UserDocumentReader = FirestoreUserDocumentReader()
) : UserDataSource {
    private val db by lazy { Firebase.firestore }

    /**
     * Profil publiczny zapisuje się jedną operacją scalającą, w której są wyłącznie pola
     * z [UserProfileFields.NAMES] oraz usunięcie pól prywatnych ze starego profilu (phone, email,
     * coinsBalance). Reguły odrzucają każdy zapis, po którym dokument publiczny nadal niesie
     * pole prywatne, więc czyszczenie musi wejść razem z resztą zmiany, a nie po niej. Scalanie
     * zamiast pełnego zastąpienia nie kasuje pól, których model nie zna.
     */
    override suspend fun saveUser(user: UserModel) {
        db.collection(FirestoreCollections.USERS).document(user.id)
            .set(UserProfileFields.profileWrite(user), SetOptions.merge())
            .await()
    }

    override suspend fun deleteUser(uid: String) {
        db.collection("users").document(uid).delete().await()
    }

    /**
     * Awaria nasłuchu wychodzi **wartością** [CurrentUserRead.Unavailable], nie `close(error)`:
     * ten strumień karmi bramkę nawigacji pod `stateIn(viewModelScope)`, gdzie nieobsłużony
     * wyjątek wywraca aplikację przy każdym starcie. Milczenie po awarii (dawne
     * `return@addSnapshotListener`) było gorsze od jednego i od drugiego: zalogowany użytkownik
     * zostawał bez żadnej odpowiedzi, więc bramka po pięciu sekundach wyrzucała go na ekran
     * powitalny, a kalendarz kręcił wskaźnik bez końca.
     */
    override fun getUser(uid: String): Flow<CurrentUserRead> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(CurrentUserRead.SignedOut)
            return@callbackFlow
        }
        val watch = UserDocumentWatch()
        val listener = db.collection(FirestoreCollections.USERS).document(uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreUserDS", "Error fetching user $uid: ${error.message}")
                }
                trySend(
                    watch.read(
                        user = snap?.toObject(UserModel::class.java)?.copy(id = uid),
                        exists = snap?.exists() == true,
                        fromCache = snap == null || snap.metadata.isFromCache,
                        error = error
                    )
                )
            }
        awaitClose { listener.remove() }
    }

    // Tozsamosc profilu to uid, czyli identyfikator dokumentu — nigdy pole "id".
    // Pole pisze klient, a getCurrentUser().id steruje authorId ogloszenia, patientId
    // rezerwacji, reviewerId opinii i userId wpisu ksiegi. Rozjechane pole zamienia
    // publikacje, rezerwacje i wystawienie opinii w odmowe regul z komunikatem
    // "nie udalo sie", bez zadnej drogi naprawy od wewnatrz aplikacji.
    private fun usersOf(snapshot: QuerySnapshot?): List<UserModel> =
        snapshot?.documents?.mapNotNull { document ->
            document.toObject(UserModel::class.java)?.copy(id = document.id)
        } ?: emptyList()

    override fun getAllByRole(role: String): Flow<List<UserModel>> = callbackFlow {
        val listener = db.collection("users")
            .whereEqualTo("role", role)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreUserDS", "Error fetching users by role $role: ${error.message}")
                    return@addSnapshotListener
                }
                trySend(usersOf(snap))
            }
        awaitClose { listener.remove() }
    }

    /**
     * `null` znaczy wyłącznie "nie ma takiego dokumentu". Nieudany odczyt wychodzi wyjątkiem,
     * bo wywołujący muszą rozróżnić pustkę od awarii: to samo `null` dla obu przypadków czyniło
     * stan [com.example.stellare.data.model.NameLookup.Unavailable] nieosiągalnym w produkcji.
     */
    override suspend fun getUserOnce(uid: String): UserModel? {
        if (uid.isEmpty()) return null
        return documents.read(uid)
    }

    override fun getAllByRoleExcluding(role: String, excludeId: String): Flow<List<UserModel>> = callbackFlow {
        val listener = db.collection("users")
            .whereEqualTo("role", role)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreUserDS", "Error fetching users by role $role: ${error.message}")
                    return@addSnapshotListener
                }
                trySend(usersOf(snap).filter { it.id != excludeId })
            }
        awaitClose { listener.remove() }
    }
}