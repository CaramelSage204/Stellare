package com.example.stellare.account

import com.example.stellare.data.model.AccountDeletionResult
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.VerificationRequestModel
import com.example.stellare.data.repository.AccountDeleter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeleterOwnedDataTest {

    private class Setup(
        slots: List<AppointmentModel> = listOf(
            slot("w1"),
            slot("w2"),
            slot("zajety", status = "BOOKED"),
            slot("odbyty", status = "COMPLETED")
        ),
        request: VerificationRequestModel? = VerificationRequestModel(uid = "u1", docImageBase64 = "YWJj")
    ) {
        val log = EventLog()
        val auth = AccountAuthDataSource(log)
        val users = AccountUserDataSource(log, listOf(UserModel(id = "u1")))
        val privateProfiles = AccountPrivateProfileDataSource(log)
        val postSource = AccountPostDataSource(log, listOf(post("p1")))
        val chatSource = AccountChatDataSource()
        val noteSource = AccountNoteDataSource(log)
        val appointments = AccountAppointmentDataSource(log, slots)
        val verification = AccountVerificationDataSource(log, request)
        val deleter = AccountDeleter(
            auth, users, privateProfiles, postSource, chatSource, noteSource, appointments, verification
        ) { AccountAuthDataSource.NOW }
    }

    @Test
    fun `sukces usuwa wolne terminy, zgloszenie i konto w ustalonej kolejnosci`() = runTest {
        val s = Setup()

        assertEquals(AccountDeletionResult.Deleted, s.deleter.delete())
        assertEquals(
            listOf(
                "slot:delete:w1",
                "slot:delete:w2",
                "private:delete",
                "user:delete",
                "post:delete:p1",
                "verification:delete",
                "auth:delete"
            ),
            s.log.events
        )
    }

    @Test
    fun `zarezerwowane i odbyte wizyty zostaja w bazie`() = runTest {
        val s = Setup()

        s.deleter.delete()

        assertEquals(
            "po usunieciu konta maja zostac wylacznie wizyty BOOKED i COMPLETED",
            listOf("zajety", "odbyty"),
            s.appointments.appointments.value.map { it.appointmentId }
        )
    }

    @Test
    fun `zgloszenie weryfikacyjne znika razem z kontem`() = runTest {
        val s = Setup()

        s.deleter.delete()

        assertNull("skan dokumentu nie moze zostac w bazie bez wlasciciela", s.verification.stored.value)
    }

    @Test
    fun `uzytkownik bez zgloszenia nie wywoluje usuwania zgloszenia`() = runTest {
        val s = Setup(request = null)

        assertEquals(AccountDeletionResult.Deleted, s.deleter.delete())
        assertFalse("brak zgloszenia nie moze generowac zapisu", "verification:delete" in s.log.events)
    }

    @Test
    fun `porazka przy wolnym terminie zatrzymuje operacje przed profilem i kontem`() = runTest {
        val s = Setup()
        s.appointments.failingIds += "w2"

        val result = s.deleter.delete() as AccountDeletionResult.Failed

        assertFalse("po porazce terminu nie wolno kasowac profilu", "user:delete" in s.log.events)
        assertFalse("po porazce terminu nie wolno kasowac konta", "auth:delete" in s.log.events)
        assertEquals(1, result.progress.slotsDeleted)
        assertEquals(2, result.progress.slotsTotal)
    }

    @Test
    fun `porazka przy zgloszeniu nie rusza konta w Auth i opisuje postep`() = runTest {
        val s = Setup()
        s.verification.deleteFailure = IllegalStateException("odmowa")

        val result = s.deleter.delete() as AccountDeletionResult.Failed

        assertFalse("po porazce zgloszenia nie wolno kasowac konta", "auth:delete" in s.log.events)
        assertTrue(result.progress.userDocumentDeleted)
        assertTrue(result.progress.verificationPlanned)
        assertFalse(result.progress.verificationDeleted)
    }

    @Test
    fun `ponowienie po porazce zgloszenia kasuje tylko to, co zostalo`() = runTest {
        val s = Setup()
        s.verification.deleteFailure = IllegalStateException("odmowa")
        s.deleter.delete()
        s.verification.deleteFailure = null
        s.log.events.clear()

        assertEquals(AccountDeletionResult.Deleted, s.deleter.delete())
        assertEquals(listOf("private:delete", "user:delete", "verification:delete", "auth:delete"), s.log.events)
    }

    @Test
    fun `blad odczytu terminow nic nie usuwa`() = runTest {
        val s = Setup()
        s.appointments.readFailure = IllegalStateException("brak sieci")

        val result = s.deleter.delete()

        assertTrue("wynik ma byc porazka, a jest $result", result is AccountDeletionResult.Failed)
        assertTrue("przy bledzie odczytu nic nie wolno usunac: ${s.log.events}", s.log.events.isEmpty())
    }
}
