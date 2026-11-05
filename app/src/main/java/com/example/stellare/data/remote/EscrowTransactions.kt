package com.example.stellare.data.remote

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.BookingConflict
import com.example.stellare.data.model.CancelOutcome
import com.example.stellare.data.model.CompletionModel
import com.example.stellare.data.model.CompletionOutcome
import com.example.stellare.data.model.EscrowModel
import com.example.stellare.data.model.EscrowSettlement
import com.example.stellare.data.model.Settlement
import com.example.stellare.data.model.UserMessageException
import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.model.WalletTransactionType
import kotlinx.coroutines.CancellationException

/**
 * Każda operacja jest JEDNĄ transakcją, a saldo i deklarację rozliczenia zapisuje polową
 * aktualizacją dokumentu prywatnego użytkownika (users/{uid}/private/contact), nigdy przez saveUser.
 * Reguły żądają, by dokument strony, której saldo się zmienia, niósł w tej samej partii pole
 * escrowSettlement równe identyfikatorowi wizyty; saveUser zapisuje wyłącznie pola profilu
 * publicznego i takiego pola nie zapisze, więc każda płatna rezerwacja dostałaby PERMISSION_DENIED.
 *
 * Saldo zmienia się WYŁĄCZNIE o przyrost ([BalanceDelta], w Firestore FieldValue.increment),
 * nigdy wartością bezwzględną. Reguła wymaga zmiany o dokładnie kwotę depozytu, ale strona
 * rozliczająca nie może odczytać dokumentu prywatnego kontrahenta: wartość bezwzględna wymagałaby
 * odczytu, który dostanie odmowę, i wypłata padałaby na stałe, zostawiając monety w depozycie.
 * Jedyny odczyt salda dotyczy konta własnego (kontrola pokrycia przy rezerwacji); istnienie konta
 * kontrahenta sprawdza się na jego dokumencie publicznym.
 * Jedna deklaracja na dokument oznacza najwyżej jedno rozliczenie na stronę w partii:
 * nie wolno łączyć dwóch rezerwacji, dwóch rozliczeń ani rozliczenia z doładowaniem.
 */
class EscrowTransactions(
    private val runner: SettlementRunner,
    private val clock: () -> Long
) {

    companion object {
        /**
         * Tytuł wpisu księgi jest zapisem powodu, a nie etykietą ekranu (ekran liczy etykietę
         * z `type`). Odzyskanie depozytu po oknie 14 dni dotyczy wizyty, która wciąż ma status
         * BOOKED i nigdy nie została odwołana, więc wspólny tytuł z odwołaniem był w bazie
         * nieprawdą — także wtedy, gdy na ekranie nie było jej widać.
         */
        const val CANCELLATION_REFUND_TITLE = "Zwrot za odwołaną wizytę"
        const val RECLAIM_REFUND_TITLE = "Zwrot depozytu nierozliczonej wizyty"

        private const val PATIENT_ACCOUNT_MISSING =
            "Konto pacjenta nie istnieje, więc nie można odwołać wizyty ani zwrócić mu monet. " +
                "Oznaczenie wizyty jako odbytej przypisze monety Tobie, więc zrób to tylko wtedy, gdy wizyta naprawdę się odbyła."
        private const val SETTLEMENT_REFUSED =
            "Nie udało się rozliczyć monet z drugą stroną wizyty, więc wizyta pozostaje zarezerwowana. " +
                "Monety pozostają w depozycie tej wizyty."
        private const val PSYCHOLOGIST_ACCOUNT_MISSING =
            "Konto psychologa nie istnieje, więc nie można odwołać wizyty. " +
                "Wizyta pozostaje zarezerwowana, a monety odzyskasz po upływie okna zwrotu (14 dni po terminie)."
    }

    suspend fun book(appointmentId: String, patientId: String, psychologistId: String, priceCoins: Int, notes: String) {
        runner.run { tx ->
            val appointment = tx.readAppointment(appointmentId)
            when (BookingConflict.classify(appointment?.status, appointment?.patientId, patientId)) {
                BookingConflict.Free -> Unit
                BookingConflict.BookedByRequester -> return@run
                BookingConflict.TakenByOther -> throw UserMessageException(BookingConflict.SLOT_TAKEN_MESSAGE)
            }
            if (appointment == null || appointment.psychologistId != psychologistId || appointment.priceCoins != priceCoins) {
                throw UserMessageException("Termin został zmieniony. Odśwież kalendarz.")
            }
            val slot = SettlementWrite.Update(
                FirestoreCollections.APPOINTMENTS,
                appointmentId,
                mapOf("patientId" to patientId, "status" to "BOOKED", "notes" to notes)
            )
            if (priceCoins <= 0) {
                tx.write(listOf(slot))
                return@run
            }
            val balance = tx.readBalance(patientId) ?: 0
            if (balance < priceCoins) throw UserMessageException("Za mało monet w portfelu.")
            tx.write(
                listOf(
                    slot,
                    SettlementWrite.Create(
                        FirestoreCollections.ESCROWS,
                        appointmentId,
                        EscrowModel(appointmentId, patientId, psychologistId, priceCoins)
                    ),
                    balanceWrite(patientId, -priceCoins, appointmentId),
                    ledgerEntry(
                        id = "${appointmentId}_$patientId",
                        userId = patientId,
                        counterpartyId = psychologistId,
                        appointmentId = appointmentId,
                        amount = -priceCoins,
                        type = WalletTransactionType.BOOKING_PAYMENT,
                        title = "Opłata za wizytę"
                    )
                )
            )
        }
    }

    suspend fun complete(appointmentId: String, marker: Pair<String, CompletionModel>?): CompletionOutcome {
        val now = clock()
        return runner.run { tx ->
            val appointment = tx.readAppointment(appointmentId) ?: throw UserMessageException("Nie znaleziono wizyty.")
            if (appointment.status == "COMPLETED") return@run completionOutcomeOf(tx.readEscrow(appointmentId))
            if (appointment.status != "BOOKED") throw UserMessageException("Wizyta nie jest zarezerwowana.")
            val escrow = tx.readEscrow(appointmentId)
            val settlement = escrow?.let {
                EscrowSettlement.decide(it, appointment.copy(status = "COMPLETED"), appointment.psychologistId, now)
            }
            val writes = mutableListOf<SettlementWrite>(
                SettlementWrite.Update(FirestoreCollections.APPOINTMENTS, appointmentId, mapOf("status" to "COMPLETED"))
            )
            if (escrow != null && settlement is Settlement.ReleaseToPsychologist) {
                requireAccount(tx, escrow.psychologistId)
                writes += releaseWrites(escrow)
            }
            marker?.let { (markerId, completion) ->
                writes += SettlementWrite.Create(FirestoreCollections.COMPLETIONS, markerId, completion)
            }
            tx.write(writes)
            if (escrow != null && settlement is Settlement.ReleaseToPsychologist) {
                CompletionOutcome.Released(settlement.amount)
            } else {
                CompletionOutcome.NoCoinsMoved
            }
        }
    }

    suspend fun cancel(appointmentId: String, cancelledBy: String): CancelOutcome {
        val now = clock()
        val missing = MissingContact()
        try {
            return runner.run { tx ->
                val appointment = tx.readAppointment(appointmentId) ?: throw UserMessageException("Nie znaleziono wizyty.")
                if (appointment.status != "BOOKED") throw UserMessageException("Wizyta nie jest zarezerwowana.")
                if (cancelledBy != appointment.psychologistId && cancelledBy != appointment.patientId) {
                    throw UserMessageException("Tylko strona wizyty może ją odwołać.")
                }
                val cancelled = appointment.copy(status = "CANCELLED", cancelledBy = cancelledBy)
                val held = tx.readEscrow(appointmentId)?.takeIf { it.status == EscrowModel.HELD }
                val settlement = held?.let { EscrowSettlement.decide(it, cancelled, cancelledBy, now) }
                val cancellation = SettlementWrite.Update(
                    FirestoreCollections.APPOINTMENTS,
                    appointmentId,
                    mapOf("status" to "CANCELLED", "cancelledBy" to cancelledBy)
                )
                when (settlement) {
                    null -> {
                        tx.write(listOf(cancellation))
                        CancelOutcome.CancelledWithoutRefund
                    }
                    is Settlement.NotSettleable -> throw UserMessageException(settlement.reason)
                    is Settlement.RefundToPatient -> {
                        requireCounterparty(tx, checkNotNull(held).patientId, cancelledBy, missing, PATIENT_ACCOUNT_MISSING)
                        tx.write(listOf(cancellation) + refundWrites(held, CANCELLATION_REFUND_TITLE))
                        CancelOutcome.Refunded(settlement.amount)
                    }
                    is Settlement.ReleaseToPsychologist -> {
                        requireCounterparty(tx, checkNotNull(held).psychologistId, cancelledBy, missing, PSYCHOLOGIST_ACCOUNT_MISSING)
                        tx.write(listOf(cancellation) + releaseWrites(held))
                        CancelOutcome.Released(settlement.amount)
                    }
                }
            }
        } catch (e: Exception) {
            throw missing.translate(e, runner)
        }
    }

    suspend fun reclaimAbandoned(appointmentId: String, patientId: String): CancelOutcome {
        val now = clock()
        return runner.run { tx ->
            val appointment = tx.readAppointment(appointmentId) ?: throw UserMessageException("Nie znaleziono wizyty.")
            val escrow = tx.readEscrow(appointmentId) ?: throw UserMessageException("Ta wizyta nie ma depozytu.")
            when (val settlement = EscrowSettlement.decide(escrow, appointment, patientId, now)) {
                is Settlement.RefundToPatient -> {
                    requireAccount(tx, escrow.patientId)
                    tx.write(refundWrites(escrow, RECLAIM_REFUND_TITLE))
                    CancelOutcome.Refunded(settlement.amount)
                }
                is Settlement.NotSettleable -> throw UserMessageException(settlement.reason)
                is Settlement.ReleaseToPsychologist -> throw UserMessageException("Tej wizyty pacjent nie może rozliczyć.")
            }
        }
    }

    suspend fun bookingLanded(appointmentId: String, patientId: String): Boolean = runner.run { tx ->
        val appointment = tx.readAppointment(appointmentId)
        if (appointment?.status != "BOOKED" || appointment.patientId != patientId) return@run false
        appointment.priceCoins <= 0 || tx.readEscrow(appointmentId)?.patientId == patientId
    }

    suspend fun completionLanded(appointmentId: String): CompletionOutcome? = runner.run { tx ->
        if (tx.readAppointment(appointmentId)?.status != "COMPLETED") return@run null
        completionOutcomeOf(tx.readEscrow(appointmentId))
    }

    private fun completionOutcomeOf(escrow: EscrowModel?): CompletionOutcome =
        if (escrow?.status == EscrowModel.RELEASED) CompletionOutcome.Released(escrow.amount) else CompletionOutcome.NoCoinsMoved

    suspend fun cancellationLanded(appointmentId: String, cancelledBy: String): CancelOutcome? = runner.run { tx ->
        val appointment = tx.readAppointment(appointmentId)
        if (appointment?.status != "CANCELLED" || appointment.cancelledBy != cancelledBy) return@run null
        val escrow = tx.readEscrow(appointmentId)
        when (escrow?.status) {
            EscrowModel.REFUNDED -> CancelOutcome.Refunded(escrow.amount)
            EscrowModel.RELEASED -> CancelOutcome.Released(escrow.amount)
            else -> CancelOutcome.CancelledWithoutRefund
        }
    }

    suspend fun reclaimLanded(appointmentId: String): CancelOutcome? = runner.run { tx ->
        tx.readEscrow(appointmentId)?.takeIf { it.status == EscrowModel.REFUNDED }?.let { CancelOutcome.Refunded(it.amount) }
    }

    suspend fun markerExists(markerId: String): Boolean =
        runner.run { tx -> tx.completionExists(markerId) }

    suspend fun loadAppointment(appointmentId: String): AppointmentModel? =
        runner.run { tx -> tx.readAppointment(appointmentId) }

    private fun requireAccount(
        tx: SettlementTransaction,
        userId: String,
        missingAccountMessage: String = "Nie znaleziono konta odbiorcy monet."
    ) {
        if (!tx.accountExists(userId)) throw UserMessageException(missingAccountMessage)
    }

    private fun requireCounterparty(
        tx: SettlementTransaction,
        userId: String,
        actorId: String,
        missing: MissingContact,
        missingAccountMessage: String
    ) {
        requireAccount(tx, userId, missingAccountMessage)
        if (userId != actorId) missing.armed = true
    }

    /**
     * Dotyczy tylko odwołania (zapis na CUDZYM dokumencie prywatnym). Dokument prywatny odbiorcy może
     * zniknąć między sprawdzeniem jego konta a zatwierdzeniem (trwa usuwanie konta). Odmowa reguł
     * (zmierzone: permission-denied, nie not-found) nie mówi, który warunek padł, więc komunikat
     * opisuje wyłącznie skutek, prawdziwy przy każdej przyczynie: nie twierdzi nic o koncie drugiej strony.
     */
    private class MissingContact {
        var armed = false

        fun translate(error: Exception, runner: SettlementRunner): Exception {
            val denied = error !is UserMessageException && error !is CancellationException && runner.isRulesDenial(error)
            return if (armed && denied) UserMessageException(SETTLEMENT_REFUSED) else error
        }
    }

    private fun balanceWrite(userId: String, delta: Int, appointmentId: String) = SettlementWrite.Update(
        FirestoreCollections.privateOf(userId),
        FirestoreCollections.CONTACT,
        mapOf("coinsBalance" to BalanceDelta(delta), "escrowSettlement" to appointmentId)
    )

    private fun escrowStatusWrite(escrow: EscrowModel, status: String) = SettlementWrite.Update(
        FirestoreCollections.ESCROWS,
        escrow.appointmentId,
        mapOf("status" to status)
    )

    private fun releaseWrites(escrow: EscrowModel): List<SettlementWrite> = listOf(
        escrowStatusWrite(escrow, EscrowModel.RELEASED),
        balanceWrite(escrow.psychologistId, escrow.amount, escrow.appointmentId),
        ledgerEntry(
            id = "${escrow.appointmentId}_${escrow.psychologistId}",
            userId = escrow.psychologistId,
            counterpartyId = escrow.patientId,
            appointmentId = escrow.appointmentId,
            amount = escrow.amount,
            type = WalletTransactionType.BOOKING_EARNING,
            title = "Wpływ za wizytę"
        )
    )

    private fun refundWrites(escrow: EscrowModel, title: String): List<SettlementWrite> = listOf(
        escrowStatusWrite(escrow, EscrowModel.REFUNDED),
        balanceWrite(escrow.patientId, escrow.amount, escrow.appointmentId),
        ledgerEntry(
            id = "${escrow.appointmentId}_refund",
            userId = escrow.patientId,
            counterpartyId = escrow.psychologistId,
            appointmentId = escrow.appointmentId,
            amount = escrow.amount,
            type = WalletTransactionType.REFUND,
            title = title
        )
    )

    private fun ledgerEntry(
        id: String,
        userId: String,
        counterpartyId: String,
        appointmentId: String,
        amount: Int,
        type: WalletTransactionType,
        title: String
    ) = SettlementWrite.Create(
        FirestoreCollections.WALLET_TRANSACTIONS,
        id,
        WalletTransactionModel(
            transactionId = id,
            userId = userId,
            counterpartyId = counterpartyId,
            appointmentId = appointmentId,
            amount = amount,
            type = type,
            title = title,
            timestamp = clock()
        )
    )
}
