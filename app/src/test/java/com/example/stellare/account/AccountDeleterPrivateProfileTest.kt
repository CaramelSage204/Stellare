package com.example.stellare.account

import com.example.stellare.data.model.AccountDeletionResult
import com.example.stellare.data.model.DeletionProgress
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.AccountDeleter
import com.example.stellare.ui.settings.AccountDeletionDisclosure
import com.example.stellare.ui.settings.BalanceState
import com.example.stellare.ui.settings.AccountDeletionMessages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeleterPrivateProfileTest {

    private class Setup {
        val log = EventLog()
        val auth = AccountAuthDataSource(log)
        val users = AccountUserDataSource(log, listOf(UserModel(id = "u1")))
        val privateProfiles = AccountPrivateProfileDataSource(log)
        val deleter = AccountDeleter(
            auth, users, privateProfiles, AccountPostDataSource(log), AccountChatDataSource(), AccountNoteDataSource(log),
            AccountAppointmentDataSource(log), AccountVerificationDataSource(log)
        ) { AccountAuthDataSource.NOW }

        fun orphanedContact(): Boolean = users.users.value.none { it.id == "u1" } && privateProfiles.profiles.value.containsKey("u1")
    }

    @Test
    fun `usuniecie konta kasuje dokument prywatny przed publicznym`() = runTest {
        val s = Setup()

        assertEquals(AccountDeletionResult.Deleted, s.deleter.delete())

        val privateAt = s.log.events.indexOf("private:delete")
        val userAt = s.log.events.indexOf("user:delete")
        assertTrue("dokument prywatny w ogóle nie został usunięty: ${s.log.events}", privateAt >= 0)
        assertTrue(
            "dokument publiczny poszedł przed prywatnym: awaria między nimi zostawia sierocy dokument z numerem telefonu " +
                "i adresem e-mail, którego nikt już nie wyświetli ani nie posprząta (${s.log.events})",
            privateAt < userAt
        )
        assertFalse(s.orphanedContact())
    }

    @Test
    fun `awaria kasowania dokumentu prywatnego nie ruszy publicznego, wiec dane osobowe nie zostana same`() = runTest {
        val s = Setup()
        s.privateProfiles.deleteFailure = IllegalStateException("odmowa")

        val result = s.deleter.delete()

        assertTrue("wynik ma być porażką: $result", result is AccountDeletionResult.Failed)
        assertFalse(
            "dokument publiczny usunięty mimo nieusuniętego prywatnego: został sierocy dokument z numerem telefonu i e-mailem",
            s.orphanedContact()
        )
        assertFalse("po porażce nie wolno kasować dokumentu publicznego", "user:delete" in s.log.events)
        assertFalse("po porażce nie wolno kasować konta", "auth:delete" in s.log.events)
        val progress = (result as AccountDeletionResult.Failed).progress
        assertFalse(progress.privateProfileDeleted)
        assertFalse(progress.userDocumentDeleted)
    }

    @Test
    fun `awaria kasowania dokumentu publicznego zostawia dokument prywatny juz usuniety i raportuje postep`() = runTest {
        val s = Setup()
        s.users.deleteFailure = IllegalStateException("odmowa")

        val result = s.deleter.delete() as AccountDeletionResult.Failed

        assertTrue(result.progress.privateProfileDeleted)
        assertFalse(result.progress.userDocumentDeleted)
        assertTrue("dane osobowe powinny zniknąć jako pierwsze", s.privateProfiles.profiles.value.isEmpty())
    }

    @Test
    fun `postep z samym dokumentem prywatnym liczy sie jako usuniecie czegokolwiek`() {
        val progress = DeletionProgress(privateProfileDeleted = true)

        assertTrue(
            "komunikat twierdziłby, że nic nie skasowano, choć portfel i dane kontaktowe już zniknęły",
            progress.anythingDeleted
        )
        val text = AccountDeletionMessages.failure(AccountDeletionResult.Failed(progress))
        assertTrue("brak informacji o usuniętych danych kontaktowych: $text", text.contains("Już usunięto: telefon, e-mail i portfel"))
        assertTrue("profil publiczny nadal istnieje: $text", text.contains("Nadal istnieje: konto logowania, profil"))
    }

    @Test
    fun `ekran usuwania mowi o saldzie z dokumentu prywatnego i nie ukrywa, gdy salda nie udalo sie sprawdzic`() {
        val withCoins = AccountDeletionDisclosure.removed(UserRole.PATIENT, 40)
        assertTrue("saldo musi być wymienione: $withCoins", withCoins.any { it.contains("40 monet") && it.contains("przepadnie bez zwrotu") })

        val empty = AccountDeletionDisclosure.removed(UserRole.PATIENT, 0)
        assertTrue("zerowe saldo nie zasługuje na wzmiankę: $empty", empty.none { it.contains("Saldo portfela") })

        val loading = AccountDeletionDisclosure.removed(UserRole.PATIENT, BalanceState.Loading)
        val unavailable = AccountDeletionDisclosure.removed(UserRole.PATIENT, BalanceState.Unavailable)
        val missing = AccountDeletionDisclosure.removed(UserRole.PATIENT, BalanceState.Missing)
        assertTrue("wczytywanie nie może wyglądać jak zero: $loading", loading.any { it == AccountDeletionDisclosure.BALANCE_LOADING })
        assertTrue(
            "nieznane saldo nie może wyglądać jak zero, bo użytkownik skasuje konto z monetami bez ostrzeżenia: $unavailable",
            unavailable.any { it == AccountDeletionDisclosure.BALANCE_UNAVAILABLE }
        )
        assertTrue(
            "brak portfela nie może twierdzić, że monety przepadną, skoro ich nie ma: $missing",
            missing.any { it == AccountDeletionDisclosure.BALANCE_MISSING } && missing.none { it.contains("przepadn") && !it.contains("nie ma monet") }
        )
        assertEquals(
            "trzy różne stany muszą mówić trzy różne rzeczy",
            3,
            listOf(AccountDeletionDisclosure.BALANCE_LOADING, AccountDeletionDisclosure.BALANCE_UNAVAILABLE, AccountDeletionDisclosure.BALANCE_MISSING).toSet().size
        )
    }
}
