package com.example.stellare.data.remote

import com.example.stellare.data.model.EscrowModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreEscrowDataSource : EscrowDataSource {
    private val collection by lazy { Firebase.firestore.collection(FirestoreCollections.ESCROWS) }

    override fun observeEscrow(appointmentId: String): Flow<EscrowModel?> = callbackFlow {
        if (appointmentId.isEmpty()) {
            trySend(null)
            return@callbackFlow
        }
        val listener = collection.document(appointmentId).addSnapshotListener { snap, error ->
            if (error != null) {
                android.util.Log.e("FirestoreEscrowDS", "Error fetching escrow $appointmentId: ${error.message}")
                close(error)
                return@addSnapshotListener
            }
            trySend(snap?.toObject(EscrowModel::class.java))
        }
        awaitClose { listener.remove() }
    }
}
