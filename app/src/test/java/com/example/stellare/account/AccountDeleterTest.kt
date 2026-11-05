package com.example.stellare.account

import com.example.stellare.data.model.AccountDeletionResult
import com.example.stellare.data.model.ChatModel
import com.example.stellare.data.model.NoteModel
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.repository.AccountDeleter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeleterTest {

    private class Setup(
        posts: List<PostModel> = listOf(post("p1"), post("p2")),
        notes: List<NoteModel> = listOf(note("n1")),
        chats: List<ChatModel> = listOf(chat("c1", psychologistId = "u1", patientId = "pacjent"))
    ) {
        val log = EventLog()
        val auth = AccountAuthDataSource(log)
        val users = AccountUserDataSource(log, listOf(UserModel(id = "u1")))
        val privateProfiles = AccountPrivateProfileDataSource(log)
        val postSource = AccountPostDataSource(log, posts)
        val chatSource = AccountChatDataSource(chats)
        val noteSource = AccountNoteDataSource(log, notes)
        val appointments = AccountAppointmentDataSource(log)
        val verification = AccountVerificationDataSource(log)
        val deleter = AccountDeleter(
            auth, users, privateProfiles, postSource, chatSource, noteSource, appointments, verification
        ) { AccountAuthDataSource.NOW }
    }

    @Test
    fun `sukces usuwa dane w kolejnosci a konto w Auth na samym koncu`() = runTest {
        val s = Setup()
        val result = s.deleter.delete()

        assertEquals(AccountDeletionResult.Deleted, result)
        assertEquals(
            listOf("note:delete:n1", "private:delete", "user:delete", "post:delete:p1", "post:delete:p2", "auth:delete"),
            s.log.events
        )
    }

    @Test
    fun `nieswieze logowanie konczy sie bez usuniecia czegokolwiek`() = runTest {
        val s = Setup()
        s.auth.lastSignIn = AccountAuthDataSource.NOW - 3_600_000

        val result = s.deleter.delete()

        assertEquals(AccountDeletionResult.RecentLoginRequired, result)
        assertTrue("przy nieswiezym logowaniu nic nie wolno usunac, a usunieto: ${s.log.events}", s.log.events.isEmpty())
    }

    @Test
    fun `nieznany czas logowania tez nic nie usuwa`() = runTest {
        val s = Setup()
        s.auth.lastSignIn = null

        assertEquals(AccountDeletionResult.RecentLoginRequired, s.deleter.delete())
        assertTrue("nieznany czas logowania nie moze uruchomic usuwania", s.log.events.isEmpty())
    }

    @Test
    fun `porazka przy ogloszeniu nie rusza konta w Auth i opisuje postep`() = runTest {
        val s = Setup()
        s.postSource.failingIds += "p2"

        val result = s.deleter.delete()

        assertTrue("wynik ma byc porazka, a jest $result", result is AccountDeletionResult.Failed)
        assertFalse("konto w Auth nie moze byc usuniete po porazce kroku danych", "auth:delete" in s.log.events)
        val progress = (result as AccountDeletionResult.Failed).progress
        assertEquals(1, progress.notesDeleted)
        assertTrue("dokument uzytkownika zostal usuniety przed porazka", progress.userDocumentDeleted)
        assertEquals(1, progress.postsDeleted)
        assertEquals(2, progress.postsTotal)
    }

    @Test
    fun `porazka przy dokumencie uzytkownika zatrzymuje ogloszenia i konto`() = runTest {
        val s = Setup()
        s.users.deleteFailure = IllegalStateException("odmowa")

        val result = s.deleter.delete() as AccountDeletionResult.Failed

        assertFalse("po porazce dokumentu nie wolno kasowac ogloszen", s.log.events.any { it.startsWith("post:delete") })
        assertFalse("po porazce dokumentu nie wolno kasowac konta", "auth:delete" in s.log.events)
        assertFalse(result.progress.userDocumentDeleted)
    }

    @Test
    fun `ponowienie po porazce dokancza usuwanie bez powtarzania skasowanego`() = runTest {
        val s = Setup()
        s.postSource.failingIds += "p2"
        s.deleter.delete()
        s.postSource.failingIds.clear()
        s.log.events.clear()

        val result = s.deleter.delete()

        assertEquals(AccountDeletionResult.Deleted, result)
        assertEquals(
            listOf("private:delete", "user:delete", "post:delete:p2", "auth:delete"),
            s.log.events
        )
    }

    @Test
    fun `wymog swiezego logowania na koncu daje porazke z postepem a nie sukces`() = runTest {
        val s = Setup()
        s.auth.deleteFailure = recentLoginRequired()

        val result = s.deleter.delete()

        assertTrue("wynik ma byc porazka, a jest $result", result is AccountDeletionResult.Failed)
        result as AccountDeletionResult.Failed
        assertTrue(result.authRecentLoginRequired)
        assertTrue(result.progress.userDocumentDeleted)
        assertEquals(2, result.progress.postsDeleted)
    }

    @Test
    fun `blad odczytu ogloszen nic nie usuwa i jest porazka`() = runTest {
        val s = Setup()
        s.postSource.readFailure = IllegalStateException("brak sieci")

        val result = s.deleter.delete()

        assertTrue("wynik ma byc porazka, a jest $result", result is AccountDeletionResult.Failed)
        assertTrue("przy bledzie odczytu nic nie wolno usunac: ${s.log.events}", s.log.events.isEmpty())
        assertFalse((result as AccountDeletionResult.Failed).progress.anythingDeleted)
    }

    @Test
    fun `notatki z czatow, w ktorych uzytkownik jest pacjentem, zostaja`() = runTest {
        val s = Setup(
            posts = emptyList(),
            notes = listOf(note("cudza", chatId = "c2", authorId = "")),
            chats = listOf(chat("c2", psychologistId = "inny", patientId = "u1"))
        )

        s.deleter.delete()

        assertFalse("notatki psychologa o pacjencie nie naleza do pacjenta", s.log.events.any { it.startsWith("note:delete") })
    }

    @Test
    fun `serwer, ktory nie odpowiada na kasowanie, daje porazke z flaga niepewnosci a nie wieczne usuwanie`() = runTest {
        val s = Setup()
        s.postSource.hangingIds += "p1"

        val result = s.deleter.delete()

        assertTrue("wynik ma byc porazka, a jest $result", result is AccountDeletionResult.Failed)
        assertTrue("porazka z braku odpowiedzi musi to zaznaczyc", (result as AccountDeletionResult.Failed).writeTimedOut)
        assertFalse("po braku odpowiedzi nie wolno kasowac konta", "auth:delete" in s.log.events)
    }

    @Test
    fun `serwer, ktory nie odpowiada na odczyt, nic nie usuwa i nie zawiesza operacji`() = runTest {
        val s = Setup()
        s.postSource.readHangs = true

        val result = s.deleter.delete()

        assertTrue("wynik ma byc porazka, a jest $result", result is AccountDeletionResult.Failed)
        assertTrue("przy braku odpowiedzi na odczyt nic nie wolno usunac: ${s.log.events}", s.log.events.isEmpty())
    }

    @Test
    fun `brak zalogowanego uzytkownika to porazka a nie sukces`() = runTest {
        val s = Setup()
        s.auth.userId.value = null

        val result = s.deleter.delete()

        assertTrue("bez zalogowanego uzytkownika nie ma czego usuwac, a wynik to $result", result is AccountDeletionResult.Failed)
        assertTrue(s.log.events.isEmpty())
    }
}
