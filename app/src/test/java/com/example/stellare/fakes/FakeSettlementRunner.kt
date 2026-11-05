package com.example.stellare.fakes

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.CompletionModel
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.UserMessageException
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.model.WalletTransactionType
import com.example.stellare.data.remote.BalanceDelta
import com.example.stellare.data.remote.FirestoreCollections
import com.example.stellare.data.remote.SettlementRunner
import com.example.stellare.data.remote.SettlementTransaction
import com.example.stellare.data.remote.SettlementWrite
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Stan bazy, na którym działają partie: wizyty, depozyty, salda, deklaracje rozliczenia,
 * księga i znaczniki odbytych wizyt. Saldo i deklaracja (`escrowSettlement`) leżą w dokumencie
 * prywatnym `users/{uid}/private/contact` ([FakeUserDataSource.privateProfiles]), tak jak w produkcji,
 * a nie w UserModel. Deklaracja zostaje w dokumencie po rozliczeniu, jak w Firestore, dopóki ktoś jej nie nadpisze.
 */
class FakeSettlementWorld(
    val appointments: MutableStateFlow<List<AppointmentModel>>,
    val users: FakeUserDataSource,
    val wallet: FakeWalletDataSource,
    val clock: () -> Long
) {
    val escrows = MutableStateFlow<Map<String, EscrowModel>>(emptyMap())
    val completions = MutableStateFlow<Map<String, CompletionModel>>(emptyMap())
    val declarations = mutableMapOf<String, String>()

    fun appointment(id: String): AppointmentModel? = appointments.value.firstOrNull { it.appointmentId == id }
    fun balance(userId: String): Int? = users.balanceOf(userId)
    fun accountExists(userId: String): Boolean = users.users.value.any { it.id == userId }
    fun ledgerEntry(id: String): WalletTransactionModel? = wallet.transactions.value.firstOrNull { it.transactionId == id }
}

/**
 * Odwzorowanie reguł `appointments`, `escrows`, `users`, `wallet_transactions` i `completions`
 * z firestore.rules dla jednej partii. Atrapa, która przepuszcza to, co reguła odrzuca, daje
 * testy sprawdzające fikcję, więc każdy warunek reguły jest tu odtworzony osobno, łącznie
 * z powiązaniem dokument-dokument (deklaracja salda wskazuje TEN depozyt). Partia jest
 * atomowa: jedno naruszenie odrzuca całość i nic się nie zmienia.
 * Wykonawca jak transakcja Firestore: odczytane wartości są sprawdzane przed zatwierdzeniem,
 * a zmiana któregokolwiek odczytu ponawia blok.
 */
class FakeSettlementRunner(private val world: FakeSettlementWorld) : SettlementRunner {
    var actor: String = ""
    var beforeFirstCommit: (suspend () -> Unit)? = null
    var attempts = 0
        private set
    var commits = 0
        private set
    val committedBatches = mutableListOf<List<SettlementWrite>>()
    var failBeforeCommit: Throwable? = null
    var loseResponseAfterCommit: Throwable? = null
    var readOnlyFailure: Throwable? = null
    var readFailureAfterLostResponse: Throwable? = null

    fun authorizeSlotCreation(appointment: AppointmentModel, signedInUserId: String?, requireNew: Boolean = true) {
        val rules = SettlementRules(world, signedInUserId ?: appointment.psychologistId, world.clock())
        if (requireNew) rules.requireNoExistingAppointment(appointment)
        rules.authorizeSlotCreation(appointment)
    }

    fun authorizeSlotDeletion(appointmentId: String, signedInUserId: String?) {
        val appointment = world.appointment(appointmentId)
        SettlementRules(world, signedInUserId ?: appointment?.psychologistId.orEmpty(), world.clock()).authorizeSlotDeletion(appointment)
    }

    override fun isRulesDenial(error: Throwable): Boolean =
        error !is UserMessageException && error.message?.startsWith("PERMISSION_DENIED") == true

    override suspend fun <T> run(block: (SettlementTransaction) -> T): T {
        repeat(MAX_ATTEMPTS) {
            attempts++
            val tx = StagedTransaction(world, actor)
            val result = block(tx)
            beforeFirstCommit?.let { hook ->
                beforeFirstCommit = null
                hook()
            }
            if (!tx.readsStillCurrent()) return@repeat
            if (tx.writes.isNotEmpty()) {
                failBeforeCommit?.let { failure ->
                    failBeforeCommit = null
                    throw failure
                }
                SettlementRules(world, actor, world.clock()).commit(tx.writes)
                commits++
                committedBatches += tx.writes.toList()
                loseResponseAfterCommit?.let { failure ->
                    loseResponseAfterCommit = null
                    readFailureAfterLostResponse?.let { readOnlyFailure = it }
                    throw failure
                }
            } else {
                readOnlyFailure?.let { throw it }
            }
            return result
        }
        throw IllegalStateException("ABORTED: too much contention")
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

private class StagedTransaction(private val world: FakeSettlementWorld, private val actor: String) : SettlementTransaction {
    val writes = mutableListOf<SettlementWrite>()
    private val appointmentReads = mutableMapOf<String, AppointmentModel?>()
    private val escrowReads = mutableMapOf<String, EscrowModel?>()
    private val balanceReads = mutableMapOf<String, Int?>()
    private val accountReads = mutableMapOf<String, Boolean>()
    private val markerReads = mutableMapOf<String, Boolean>()

    override fun readAppointment(appointmentId: String): AppointmentModel? {
        check(writes.isEmpty()) { "Firestore: odczyt po zapisie w transakcji jest niedozwolony" }
        return world.appointment(appointmentId).also { appointmentReads[appointmentId] = it }
    }

    override fun readEscrow(appointmentId: String): EscrowModel? {
        check(writes.isEmpty()) { "Firestore: odczyt po zapisie w transakcji jest niedozwolony" }
        return world.escrows.value[appointmentId].also { escrowReads[appointmentId] = it }
    }

    override fun readBalance(userId: String): Int? {
        check(writes.isEmpty()) { "Firestore: odczyt po zapisie w transakcji jest niedozwolony" }
        check(userId == actor) { "PERMISSION_DENIED: odczyt dokumentu prywatnego użytkownika $userId przez $actor" }
        return world.balance(userId).also { balanceReads[userId] = it }
    }

    override fun accountExists(userId: String): Boolean {
        check(writes.isEmpty()) { "Firestore: odczyt po zapisie w transakcji jest niedozwolony" }
        return world.accountExists(userId).also { accountReads[userId] = it }
    }

    override fun completionExists(markerId: String): Boolean {
        check(writes.isEmpty()) { "Firestore: odczyt po zapisie w transakcji jest niedozwolony" }
        return (markerId in world.completions.value).also { markerReads[markerId] = it }
    }

    override fun write(writes: List<SettlementWrite>) {
        this.writes += writes
    }

    fun readsStillCurrent(): Boolean =
        appointmentReads.all { (id, seen) -> world.appointment(id) == seen } &&
            escrowReads.all { (id, seen) -> world.escrows.value[id] == seen } &&
            balanceReads.all { (id, seen) -> world.balance(id) == seen } &&
            accountReads.all { (id, seen) -> world.accountExists(id) == seen } &&
            markerReads.all { (id, seen) -> (id in world.completions.value) == seen }
}

private class SettlementRules(
    private val world: FakeSettlementWorld,
    private val actor: String,
    private val now: Long
) {
    private val appointmentsAfter = world.appointments.value.associateBy { it.appointmentId }.toMutableMap()
    private val escrowsAfter = world.escrows.value.toMutableMap()
    private val balancesAfter = world.users.privateProfiles.profiles.value.mapValues { it.value.coinsBalance }.toMutableMap()
    private val declarationsAfter = world.declarations.toMutableMap()
    private val ledgerAfter = mutableListOf<WalletTransactionModel>()
    private val markersAfter = mutableMapOf<String, CompletionModel>()

    fun commit(writes: List<SettlementWrite>) {
        val documents = writes.map { it.collection to it.id }
        if (documents.size != documents.toSet().size) deny("dwa zapisy tego samego dokumentu w jednej partii")
        writes.forEach(::stage)
        writes.forEach(::authorize)
        apply()
    }

    fun requireNoExistingAppointment(appointment: AppointmentModel) {
        if (world.appointment(appointment.appointmentId) != null) deny("zapis nad istniejącą wizytą to aktualizacja, której saveAppointment nie przewiduje")
    }

    fun authorizeSlotCreation(appointment: AppointmentModel) {
        if (appointment.psychologistId != actor) deny("termin wystawia wyłącznie jego psycholog, a nie $actor")
        val author = world.users.users.value.firstOrNull { it.id == appointment.psychologistId }
            ?: deny("autor terminu nie istnieje")
        if (appointment.status != "FREE") deny("termin wystawia się wyłącznie jako wolny")
        if (appointment.patientId != null) deny("wolny termin nie ma pacjenta")
        if (appointment.cancelledBy != null) deny("wolny termin nie ma odwołującego")
        if (appointment.priceCoins < 0) deny("cena terminu nie może być ujemna")
        if (appointment.priceCoins != author.pricePerSession.toInt()) deny("cena terminu musi zgadzać się z ceną sesji w profilu psychologa")
        if (appointment.startsAtMillis <= now) deny("termin musi zaczynać się w przyszłości")
        if (author.role != UserRole.PSYCHOLOGIST && author.role != UserRole.PSYCHOLOGY_STUDENT) deny("terminy wystawia wyłącznie psycholog")
    }

    fun authorizeSlotDeletion(appointment: AppointmentModel?) {
        if (appointment == null) deny("nie można skasować terminu, którego nie ma")
        if (appointment.psychologistId != actor) deny("skasować można tylko własny termin, a nie termin psychologa ${appointment.psychologistId}")
        if (appointment.status != "FREE") deny("skasować można tylko własny, niezajęty termin")
    }

    private fun deny(reason: String): Nothing = throw IllegalStateException("PERMISSION_DENIED: $reason")

    private fun stage(write: SettlementWrite) {
        when (write) {
            is SettlementWrite.Create -> stageCreate(write)
            is SettlementWrite.Update -> stageUpdate(write)
        }
    }

    private fun stageCreate(write: SettlementWrite.Create) {
        when (write.collection) {
            FirestoreCollections.ESCROWS -> {
                if (write.id in world.escrows.value) deny("depozyt już istnieje; zapis nad istniejącym dokumentem to aktualizacja")
                escrowsAfter[write.id] = write.data as EscrowModel
            }
            FirestoreCollections.WALLET_TRANSACTIONS -> {
                if (world.ledgerEntry(write.id) != null) deny("księga jest niezmienna")
                ledgerAfter += write.data as WalletTransactionModel
            }
            FirestoreCollections.COMPLETIONS -> {
                if (write.id in world.completions.value) deny("znacznik odbytej wizyty już istnieje")
                markersAfter[write.id] = write.data as CompletionModel
            }
            else -> deny("brak reguły tworzenia dla ${write.collection}")
        }
    }

    private fun stageUpdate(write: SettlementWrite.Update) {
        when (write.collection) {
            FirestoreCollections.APPOINTMENTS -> {
                val before = appointmentsAfter[write.id] ?: deny("wizyta nie istnieje")
                appointmentsAfter[write.id] = before.withFields(write.fields)
            }
            FirestoreCollections.ESCROWS -> {
                val before = escrowsAfter[write.id] ?: deny("depozyt nie istnieje")
                escrowsAfter[write.id] = before.copy(status = write.fields.getValue("status") as String)
            }
            FirestoreCollections.USERS -> deny("profil publiczny nie niesie pól prywatnych ani salda, a tu nie ma reguły zapisu profilu")
            else -> {
                val uid = privateOwner(write.collection) ?: deny("brak reguły aktualizacji dla ${write.collection}")
                if (write.id != FirestoreCollections.CONTACT) deny("w ${write.collection} istnieje tylko dokument ${FirestoreCollections.CONTACT}")
                if (uid !in balancesAfter) deny("NOT_FOUND: dokument prywatny użytkownika $uid nie istnieje")
                val unknown = write.fields.keys - setOf("coinsBalance", "escrowSettlement", "phone", "email")
                if (unknown.isNotEmpty()) deny("pola $unknown nie należą do dokumentu prywatnego")
                write.fields["coinsBalance"]?.let { balancesAfter[uid] = newBalance(balancesAfter.getValue(uid), it) }
                write.fields["escrowSettlement"]?.let { declarationsAfter[uid] = it as String }
            }
        }
    }

    private fun privateOwner(collection: String): String? =
        Regex("^${FirestoreCollections.USERS}/([^/]+)/${FirestoreCollections.PRIVATE}$").find(collection)?.groupValues?.get(1)

    private fun newBalance(current: Int, value: Any?): Int = when (value) {
        is BalanceDelta -> current + value.amount
        is Int -> value
        else -> deny("saldo musi być liczbą całkowitą, a jest $value")
    }

    private fun AppointmentModel.withFields(fields: Map<String, Any?>): AppointmentModel {
        var changed = this
        fields.forEach { (name, value) ->
            changed = when (name) {
                "status" -> changed.copy(status = value as String)
                "patientId" -> changed.copy(patientId = value as String?)
                "notes" -> changed.copy(notes = value as String)
                "cancelledBy" -> changed.copy(cancelledBy = value as String?)
                else -> deny("pole $name wizyty nie jest przewidziane w atrapie")
            }
        }
        return changed
    }

    private fun authorize(write: SettlementWrite) {
        when (write) {
            is SettlementWrite.Create -> when (write.collection) {
                FirestoreCollections.ESCROWS -> authorizeEscrowCreate(write.id)
                FirestoreCollections.WALLET_TRANSACTIONS -> authorizeLedger(write.data as WalletTransactionModel, write.id)
                FirestoreCollections.COMPLETIONS -> authorizeMarker(write.data as CompletionModel, write.id)
            }
            is SettlementWrite.Update -> when (write.collection) {
                FirestoreCollections.APPOINTMENTS -> authorizeAppointment(write.id)
                FirestoreCollections.ESCROWS -> authorizeEscrowUpdate(write.id)
                else -> privateOwner(write.collection)?.let(::authorizeUser)
            }
        }
    }

    private fun balanceBefore(uid: String): Int = world.balance(uid) ?: deny("użytkownik nie istnieje")
    private fun balanceAfter(uid: String): Int = balancesAfter[uid] ?: deny("użytkownik nie istnieje")
    private fun declaration(uid: String): String = declarationsAfter[uid] ?: ""

    private fun authorizeEscrowCreate(appointmentId: String) {
        val escrow = escrowsAfter.getValue(appointmentId)
        val before = world.appointment(appointmentId) ?: deny("wizyta nie istnieje")
        val after = appointmentsAfter.getValue(appointmentId)
        if (escrow.appointmentId != appointmentId) deny("depozyt wskazuje inną wizytę")
        if (escrow.patientId != actor) deny("depozyt zakłada wyłącznie płacący pacjent")
        if (escrow.status != EscrowModel.HELD) deny("depozyt powstaje jako HELD")
        if (escrow.amount <= 0) deny("depozyt wymaga kwoty dodatniej")
        if (before.status != "FREE" || after.status != "BOOKED") deny("depozyt powstaje tylko przy rezerwacji wolnego terminu")
        if (after.patientId != actor) deny("wizyta musi być zarezerwowana na płacącego")
        if (escrow.psychologistId != before.psychologistId) deny("depozyt wskazuje innego psychologa niż wizyta")
        if (escrow.amount != before.priceCoins) deny("kwota depozytu różni się od ceny wizyty")
        if (balanceAfter(actor) != balanceBefore(actor) - escrow.amount) deny("depozyt bez zmniejszenia salda pacjenta to bicie monet")
        if (declaration(actor) != appointmentId) deny("płatna rezerwacja bez deklaracji rozliczenia nie przejdzie")
    }

    private fun authorizeEscrowUpdate(appointmentId: String) {
        val before = world.escrows.value.getValue(appointmentId)
        val after = escrowsAfter.getValue(appointmentId)
        val appointmentBefore = world.appointment(appointmentId) ?: deny("wizyta nie istnieje")
        val appointmentAfter = appointmentsAfter.getValue(appointmentId)
        if (before.status != EscrowModel.HELD) deny("depozyt rozlicza się dokładnie raz")
        if (before.copy(status = after.status) != after) deny("w depozycie wolno zmienić wyłącznie status")
        val allowed = when (after.status) {
            EscrowModel.RELEASED -> releaseAllowed(before, appointmentBefore, appointmentAfter)
            EscrowModel.REFUNDED -> refundAllowed(before, appointmentBefore, appointmentAfter)
            else -> false
        }
        if (!allowed) deny("rozliczenie depozytu odrzucone: ${after.status} przez $actor")
    }

    private fun releaseAllowed(escrow: EscrowModel, before: AppointmentModel, after: AppointmentModel): Boolean {
        val psychologist = escrow.psychologistId
        if (balanceAfter(psychologist) != balanceBefore(psychologist) + escrow.amount) return false
        if (declaration(psychologist) != escrow.appointmentId) return false
        val byPsychologist = actor == psychologist && after.status == "COMPLETED"
        val byPatientAfterLateCancel = actor == escrow.patientId &&
            after.status == "CANCELLED" &&
            after.cancelledBy == escrow.patientId &&
            before.startsAtMillis - now <= WINDOW_24H
        return byPsychologist || byPatientAfterLateCancel
    }

    private fun refundAllowed(escrow: EscrowModel, before: AppointmentModel, after: AppointmentModel): Boolean {
        val patient = escrow.patientId
        if (balanceAfter(patient) != balanceBefore(patient) + escrow.amount) return false
        if (declaration(patient) != escrow.appointmentId) return false
        val afterCancellation = after.status == "CANCELLED" &&
            ((actor == patient && (after.cancelledBy == escrow.psychologistId || before.startsAtMillis - now > WINDOW_24H)) ||
                actor == escrow.psychologistId)
        val afterWindow = actor == patient &&
            before.status == "BOOKED" &&
            after.status == "BOOKED" &&
            now - before.startsAtMillis > WINDOW_14D
        return afterCancellation || afterWindow
    }

    private fun authorizeAppointment(id: String) {
        val before = world.appointment(id) ?: deny("wizyta nie istnieje")
        val after = appointmentsAfter.getValue(id)
        if (before == after) return
        val unchangedTerms = after.psychologistId == before.psychologistId &&
            after.date == before.date &&
            after.time == before.time &&
            after.startsAtMillis == before.startsAtMillis &&
            after.priceCoins == before.priceCoins
        if (!unchangedTerms) deny("termin, cena i psycholog wizyty są niezmienne")
        val allowed = if (before.status == "FREE" && after.status == "BOOKED") {
            bookingAllowed(id, before, after)
        } else {
            statusChangeAllowed(id, before, after)
        }
        if (!allowed) deny("zmiana wizyty ${before.status} -> ${after.status} przez $actor odrzucona")
    }

    private fun bookingAllowed(id: String, before: AppointmentModel, after: AppointmentModel): Boolean {
        val escrow = escrowsAfter[id]
        val depositOk = before.priceCoins <= 0 || (
            id !in world.escrows.value &&
                escrow != null &&
                escrow.status == EscrowModel.HELD &&
                escrow.appointmentId == id &&
                escrow.patientId == actor &&
                escrow.psychologistId == before.psychologistId &&
                escrow.amount == before.priceCoins
            )
        return after.patientId == actor &&
            after.patientId != before.psychologistId &&
            after.cancelledBy == null &&
            depositOk &&
            before.startsAtMillis > now
    }

    private fun statusChangeAllowed(id: String, before: AppointmentModel, after: AppointmentModel): Boolean {
        val party = actor == before.psychologistId || actor == before.patientId
        val statusOk = after.status in setOf("FREE", "CANCELLED", "COMPLETED")
        val patientOk = if (after.status == "FREE") after.patientId == null else after.patientId == before.patientId
        val completionOk = after.status != "COMPLETED" || (before.status == "BOOKED" && before.startsAtMillis <= now)
        val statusChangeOk = after.status == before.status ||
            after.status == "COMPLETED" ||
            (before.status != "COMPLETED" && (actor == before.psychologistId || before.startsAtMillis > now))
        val cancelledByOk = if (after.status == "CANCELLED") {
            if (before.status == "CANCELLED") after.cancelledBy == before.cancelledBy else after.cancelledBy == actor
        } else {
            after.cancelledBy == null
        }
        val noReturnToFreeWithDeposit = after.status != "FREE" || id !in escrowsAfter
        return party && statusOk && patientOk && completionOk && statusChangeOk && cancelledByOk && noReturnToFreeWithDeposit
    }

    private fun authorizeUser(uid: String) {
        val before = balanceBefore(uid)
        val after = balanceAfter(uid)
        if (actor == uid) {
            if (after == before) return
            val allowed = after >= 0 && (after < before || after - before <= TOP_UP_LIMIT || settlementDeclared(uid, before, after))
            if (!allowed) deny("saldo rośnie wyłącznie przez doładowanie albo rozliczenie depozytu")
        } else {
            if (!settlementDeclared(uid, before, after)) deny("cudze saldo rośnie wyłącznie przy rozliczeniu depozytu")
        }
    }

    private fun settlementDeclared(uid: String, before: Int, after: Int): Boolean {
        val id = declaration(uid)
        val amount = after - before
        val escrowBefore = world.escrows.value[id] ?: return false
        val escrowAfter = escrowsAfter[id] ?: return false
        return id.isNotEmpty() &&
            amount > 0 &&
            escrowBefore.status == EscrowModel.HELD &&
            escrowBefore.amount == amount &&
            ((escrowAfter.status == EscrowModel.RELEASED && escrowBefore.psychologistId == uid) ||
                (escrowAfter.status == EscrowModel.REFUNDED && escrowBefore.patientId == uid))
    }

    private fun authorizeLedger(entry: WalletTransactionModel, id: String) {
        if (entry.transactionId != id) deny("identyfikator wpisu księgi musi być zgodny z dokumentem")
        if (entry.amount == 0) deny("wpis księgi musi mieć niezerową kwotę")
        val own = entry.userId == actor
        if (!own && !payoutEntryAllowed(entry) && !refundEntryAllowed(entry)) {
            deny("wpis w cudzej księdze bez przestawienia depozytu")
        }
    }

    private fun payoutEntryAllowed(entry: WalletTransactionModel): Boolean {
        val id = entry.appointmentId ?: return false
        val before = world.escrows.value[id] ?: return false
        val after = escrowsAfter[id] ?: return false
        return entry.type == WalletTransactionType.BOOKING_EARNING &&
            before.status == EscrowModel.HELD &&
            after.status == EscrowModel.RELEASED &&
            actor == before.patientId &&
            entry.counterpartyId == actor &&
            entry.userId == before.psychologistId &&
            entry.amount == before.amount
    }

    private fun refundEntryAllowed(entry: WalletTransactionModel): Boolean {
        val id = entry.appointmentId ?: return false
        val before = world.escrows.value[id] ?: return false
        val after = escrowsAfter[id] ?: return false
        return entry.type == WalletTransactionType.REFUND &&
            before.status == EscrowModel.HELD &&
            after.status == EscrowModel.REFUNDED &&
            actor == before.psychologistId &&
            entry.counterpartyId == actor &&
            entry.userId == before.patientId &&
            entry.amount == before.amount
    }

    private fun authorizeMarker(marker: CompletionModel, id: String) {
        val before = world.appointment(marker.appointmentId) ?: deny("znacznik wskazuje nieistniejącą wizytę")
        val after = appointmentsAfter.getValue(marker.appointmentId)
        val allowed = id == "${marker.psychologistId}_${marker.patientId}" &&
            before.status == "BOOKED" &&
            after.status == "COMPLETED" &&
            before.startsAtMillis <= now &&
            before.psychologistId == marker.psychologistId &&
            before.patientId == marker.patientId
        if (!allowed) deny("znacznik odbytej wizyty bez przejścia BOOKED -> COMPLETED w tej samej partii")
    }

    private fun apply() {
        world.appointments.value = world.appointments.value.map { appointmentsAfter.getValue(it.appointmentId) }
        world.escrows.value = escrowsAfter
        val profiles = world.users.privateProfiles
        profiles.profiles.value = profiles.profiles.value.mapValues { (uid, profile) -> profile.copy(coinsBalance = balancesAfter.getValue(uid)) }
        world.declarations.clear()
        world.declarations.putAll(declarationsAfter)
        world.wallet.transactions.value = world.wallet.transactions.value + ledgerAfter
        world.completions.value = world.completions.value + markersAfter
    }

    private companion object {
        const val WINDOW_24H = 86_400_000L
        const val WINDOW_14D = 1_209_600_000L
        const val TOP_UP_LIMIT = 1000
    }
}
