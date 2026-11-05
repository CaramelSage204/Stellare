package com.example.stellare.data.remote

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.EscrowModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

data class BalanceDelta(val amount: Int)

internal fun firestoreValue(value: Any?): Any? =
    if (value is BalanceDelta) FieldValue.increment(value.amount.toLong()) else value

internal fun firestoreFields(fields: Map<String, Any?>): Map<String, Any?> =
    fields.mapValues { (_, value) -> firestoreValue(value) }

internal interface WriteSink {
    fun set(collection: String, id: String, data: Any)
    fun update(collection: String, id: String, fields: Map<String, Any?>)
}

internal fun applyWrites(writes: List<SettlementWrite>, sink: WriteSink) {
    writes.forEach { write ->
        when (write) {
            is SettlementWrite.Create -> sink.set(write.collection, write.id, firestoreCreateData(write.data))
            is SettlementWrite.Update -> sink.update(write.collection, write.id, firestoreFields(write.fields))
        }
    }
}

internal fun firestoreCreateData(data: Any): Any {
    require(data !is BalanceDelta && data !is Map<*, *>) {
        "Tworzenie dokumentu przyjmuje model, nie mapę pól: przyrost salda zapisany jako surowa wartość zniszczyłby monety"
    }
    return data
}

sealed interface SettlementWrite {
    val collection: String
    val id: String

    data class Create(override val collection: String, override val id: String, val data: Any) : SettlementWrite
    data class Update(override val collection: String, override val id: String, val fields: Map<String, Any?>) : SettlementWrite
}

interface SettlementTransaction {
    fun readAppointment(appointmentId: String): AppointmentModel?
    fun readEscrow(appointmentId: String): EscrowModel?
    fun readBalance(userId: String): Int?
    fun accountExists(userId: String): Boolean
    fun completionExists(markerId: String): Boolean
    fun write(writes: List<SettlementWrite>)
}

interface SettlementRunner {
    suspend fun <T> run(block: (SettlementTransaction) -> T): T
    fun isRulesDenial(error: Throwable): Boolean
}

class FirestoreSettlementRunner(
    private val db: Lazy<FirebaseFirestore> = lazy { Firebase.firestore }
) : SettlementRunner {
    override suspend fun <T> run(block: (SettlementTransaction) -> T): T {
        val firestore = db.value
        return firestore.runTransaction { transaction ->
            block(FirestoreSettlementTransaction(firestore, transaction))
        }.await()
    }

    override fun isRulesDenial(error: Throwable): Boolean = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .any { it.code == FirebaseFirestoreException.Code.PERMISSION_DENIED || it.code == FirebaseFirestoreException.Code.NOT_FOUND }
}

private class FirestoreSettlementTransaction(
    private val db: FirebaseFirestore,
    private val transaction: Transaction
) : SettlementTransaction {

    // Tozsamosc wizyty bierze sie ze sciezki, pod ktora transakcja ja odczytala, nie
    // z jej tresci: CompletionFlow uzywa appointment.appointmentId jako celu zapisu
    // zakonczenia i jako pola znacznika odbytej wizyty.
    override fun readAppointment(appointmentId: String): AppointmentModel? =
        transaction.get(db.collection(FirestoreCollections.APPOINTMENTS).document(appointmentId))
            .toObject(AppointmentModel::class.java)?.copy(appointmentId = appointmentId)

    override fun readEscrow(appointmentId: String): EscrowModel? =
        transaction.get(db.collection(FirestoreCollections.ESCROWS).document(appointmentId))
            .toObject(EscrowModel::class.java)

    override fun readBalance(userId: String): Int? {
        val contact = transaction.get(privateContact(userId))
        return if (contact.exists()) (contact.getLong("coinsBalance") ?: 0L).toInt() else null
    }

    override fun accountExists(userId: String): Boolean =
        transaction.get(db.collection(FirestoreCollections.USERS).document(userId)).exists()

    private fun privateContact(userId: String) =
        db.collection(FirestoreCollections.privateOf(userId)).document(FirestoreCollections.CONTACT)

    override fun completionExists(markerId: String): Boolean =
        transaction.get(db.collection(FirestoreCollections.COMPLETIONS).document(markerId)).exists()

    override fun write(writes: List<SettlementWrite>) {
        applyWrites(writes, object : WriteSink {
            override fun set(collection: String, id: String, data: Any) {
                transaction.set(db.collection(collection).document(id), data)
            }

            override fun update(collection: String, id: String, fields: Map<String, Any?>) {
                transaction.update(db.collection(collection).document(id), fields)
            }
        })
    }
}
