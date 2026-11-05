package com.example.stellare.fakes

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.remote.FirestoreCollections
import com.example.stellare.data.remote.SettlementWrite

class EscrowTestWorld(
    patients: Map<String, Int> = mapOf("k1" to 200, "k2" to 200),
    psychologists: Map<String, Int> = mapOf("p1" to 50, "p2" to 50),
    slots: List<AppointmentModel> = emptyList(),
    startMillis: Long = START
) {
    var now = startMillis

    private val seededCoins = patients.values.sum() + psychologists.values.sum()

    val users = FakeUserDataSource(
        patients.keys.map { id -> UserModel(id = id, role = UserRole.PATIENT) } +
            psychologists.keys.map { id -> UserModel(id = id, role = UserRole.PSYCHOLOGIST) },
        balances = patients + psychologists
    )
    val wallet = FakeWalletDataSource(users)
    val source = FakeAppointmentDataSource(slots, users, wallet) { now }

    init {
        check(seededCoins > 0) { "świat bez monet zrobiłby z asercji sumy tautologię" }
        check(coinsInSystem() == seededCoins) {
            "przyrząd pomiarowy rozjechał się z fixturą: suma w systemie ${coinsInSystem()}, zasiano $seededCoins"
        }
        slots.forEach { slot ->
            val author = users.users.value.first { it.id == slot.psychologistId }
            val profileBefore = author.pricePerSession
            users.users.value = users.users.value.map { if (it.id == author.id) it.copy(pricePerSession = slot.priceCoins.toDouble()) else it }
            try {
                source.transactions.authorizeSlotCreation(slot.copy(status = "FREE", patientId = null, cancelledBy = null), slot.psychologistId, requireNew = false)
            } catch (e: IllegalStateException) {
                throw IllegalStateException("fixtura ${slot.appointmentId} nie mogłaby powstać w produkcji: ${e.message}", e)
            } finally {
                users.users.value = users.users.value.map { if (it.id == author.id) it.copy(pricePerSession = profileBefore) else it }
            }
        }
    }

    fun balance(userId: String): Int = checkNotNull(users.balanceOf(userId)) { "Brak dokumentu prywatnego $userId" }
    fun appointment(id: String): AppointmentModel = source.appointments.value.first { it.appointmentId == id }
    fun escrow(id: String): EscrowModel? = source.escrows.value[id]
    fun heldTotal(): Int = source.escrows.value.values.filter { it.status == EscrowModel.HELD }.sumOf { it.amount }
    fun coinsInSystem(): Int {
        val withoutWallet = users.users.value.map { it.id }.filter { users.balanceOf(it) == null }
        check(withoutWallet.isEmpty()) { "konta bez dokumentu prywatnego wypadłyby z sumy po cichu: $withoutWallet" }
        return users.privateProfiles.profiles.value.values.sumOf { it.coinsBalance } + heldTotal()
    }
    fun ledger(id: String) = wallet.transactions.value.firstOrNull { it.transactionId == id }

    fun topUp(userId: String, amount: Int) {
        users.privateProfiles.setBalance(userId, balance(userId) + amount)
    }

    fun deleteAccount(userId: String) {
        users.users.value = users.users.value.filterNot { it.id == userId }
        users.privateProfiles.profiles.value = users.privateProfiles.profiles.value - userId
    }

    suspend fun attack(actor: String, writes: List<SettlementWrite>) {
        source.transactions.actor = actor
        source.transactions.run { tx -> tx.write(writes) }
    }

    companion object {
        const val START = 1_800_000_000_000L
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR

        fun contactWrite(userId: String, fields: Map<String, Any?>) =
            SettlementWrite.Update(FirestoreCollections.privateOf(userId), FirestoreCollections.CONTACT, fields)

        fun slot(id: String, psychologistId: String, price: Int, startsAt: Long = START + 3 * DAY, hour: Int = 10) = AppointmentModel(
            appointmentId = id,
            psychologistId = psychologistId,
            startsAtMillis = startsAt,
            date = "2027-01-15",
            time = "$hour:00",
            status = "FREE",
            priceCoins = price
        )
    }
}
