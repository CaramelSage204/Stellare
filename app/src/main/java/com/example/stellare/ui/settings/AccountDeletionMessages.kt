package com.example.stellare.ui.settings

import com.example.stellare.data.model.AccountDeletionResult
import com.example.stellare.data.model.DeletionProgress
import com.example.stellare.ui.PolishPlural

object AccountDeletionMessages {

    const val RECENT_LOGIN_REQUIRED =
        "Do usunięcia konta potrzebne jest świeże logowanie. Nic nie zostało usunięte. " +
            "Wyloguj się i zaloguj ponownie, a potem wróć do Ustawień i usuń konto."

    const val NOTHING_REMOVED =
        "Konto NIE zostało usunięte i nic nie zostało skasowane. Sprawdź połączenie i spróbuj ponownie."

    const val WRITE_TIMED_OUT =
        "Serwer nie odpowiedział w porę, więc nie wiemy, czy ostatni krok się wykonał. " +
            "Mógł zostać przyjęty i dokończyć się po odzyskaniu połączenia."

    fun failure(failed: AccountDeletionResult.Failed): String {
        val progress = failed.progress
        if (!progress.anythingDeleted && !failed.writeTimedOut) return NOTHING_REMOVED
        val parts = mutableListOf("Konto NIE zostało usunięte.")
        if (progress.anythingDeleted) {
            parts += "Usuwanie przerwało się w połowie."
            parts += "Już usunięto: ${removedList(progress)}."
            parts += "Nadal istnieje: ${remainingList(progress)}."
        }
        if (failed.writeTimedOut) parts += WRITE_TIMED_OUT
        parts += if (failed.authRecentLoginRequired) {
            "Żeby dokończyć, zaloguj się ponownie. Jeśli aplikacja poprosi o uzupełnienie profilu, " +
                "uzupełnij go i ponów usuwanie konta w Ustawieniach."
        } else {
            "Sprawdź połączenie i ponów próbę — kolejne usuwanie dokończy to, co zostało."
        }
        return parts.joinToString(" ")
    }

    private fun removedList(progress: DeletionProgress): String {
        val items = mutableListOf<String>()
        if (progress.userDocumentDeleted) items += "profil"
        if (progress.privateProfileDeleted && !progress.userDocumentDeleted) items += PRIVATE_PROFILE
        if (progress.postsDeleted > 0) items += posts(progress.postsDeleted, progress.postsTotal)
        if (progress.notesDeleted > 0) items += notes(progress.notesDeleted, progress.notesTotal)
        if (progress.slotsDeleted > 0) items += slots(progress.slotsDeleted, progress.slotsTotal)
        if (progress.verificationDeleted) items += VERIFICATION_REQUEST
        return items.joinToString(", ")
    }

    private fun remainingList(progress: DeletionProgress): String {
        val items = mutableListOf<String>()
        items += "konto logowania"
        if (!progress.userDocumentDeleted) items += "profil"
        val postsLeft = progress.postsTotal - progress.postsDeleted
        if (postsLeft > 0) items += "$postsLeft ${PolishPlural.form(postsLeft, "ogłoszenie", "ogłoszenia", "ogłoszeń")}"
        val notesLeft = progress.notesTotal - progress.notesDeleted
        if (notesLeft > 0) items += "$notesLeft ${PolishPlural.form(notesLeft, "notatka", "notatki", "notatek")}"
        val slotsLeft = progress.slotsTotal - progress.slotsDeleted
        if (slotsLeft > 0) {
            items += "$slotsLeft ${PolishPlural.form(slotsLeft, "wolny termin", "wolne terminy", "wolnych terminów")}"
        }
        if (progress.verificationPlanned && !progress.verificationDeleted) items += VERIFICATION_REQUEST
        return items.joinToString(", ")
    }

    private fun posts(done: Int, total: Int): String =
        "ogłoszenia ($done z $total)"

    private fun notes(done: Int, total: Int): String =
        "notatki ($done z $total)"

    private fun slots(done: Int, total: Int): String =
        "wolne terminy ($done z $total)"

    private const val PRIVATE_PROFILE = "telefon, e-mail i portfel"
    private const val VERIFICATION_REQUEST = "zgłoszenie weryfikacyjne"
}
