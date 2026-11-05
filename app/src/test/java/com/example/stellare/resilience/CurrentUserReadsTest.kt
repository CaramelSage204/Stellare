package com.example.stellare.resilience

import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.CurrentUserReads
import com.example.stellare.ui.UserFacingError
import com.example.stellare.ui.lastKnownUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class CurrentUserReadsTest {

    private val psycholog = UserModel(id = "p1", firstName = "Jan", role = UserRole.PSYCHOLOGIST)
    private val awaria = CurrentUserRead.Unavailable(IOException("odczyt padl"))

    @Test
    fun `awaria odczytu zostawia ostatni znany profil`() {
        assertEquals(
            "zgaszenie profilu przy chwilowej awarii gasi cala aplikacje: avatar, role, ceny i listy " +
                "stoja na tym jednym obiekcie",
            psycholog,
            CurrentUserReads.kept(psycholog, awaria)
        )
    }

    @Test
    fun `potwierdzony brak profilu kasuje ostatni znany profil`() {
        assertNull(
            "po potwierdzonym usunieciu dokumentu dalsze rysowanie starego profilu przedstawialoby " +
                "nieistniejace dane jako prawde",
            CurrentUserReads.kept(psycholog, CurrentUserRead.Absent)
        )
    }

    @Test
    fun `wylogowanie kasuje ostatni znany profil`() {
        assertNull(
            "po wylogowaniu profil poprzedniego konta nie moze zostac na ekranie",
            CurrentUserReads.kept(psycholog, CurrentUserRead.SignedOut)
        )
    }

    @Test
    fun `komunikat powstaje wylacznie dla awarii odczytu`() {
        assertNull(CurrentUserReads.failureMessage(CurrentUserRead.Loaded(psycholog)))
        assertNull(CurrentUserReads.failureMessage(CurrentUserRead.Absent))
        assertNull(CurrentUserReads.failureMessage(CurrentUserRead.SignedOut))
        assertNotNull(
            "bez komunikatu awaria odczytu jest niewidoczna, a to byl caly defekt R8",
            CurrentUserReads.failureMessage(awaria)
        )
    }

    @Test
    fun `brak polaczenia mowi o braku polaczenia`() {
        val offline = CurrentUserRead.Unavailable(
            FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
        )

        assertEquals(UserFacingError.NO_CONNECTION, CurrentUserReads.failureMessage(offline))
    }

    @Test
    fun `strumien ostatniego znanego profilu nie powtarza tej samej wartosci`() = runTest {
        val odczyty = flowOf(
            CurrentUserRead.SignedOut,
            CurrentUserRead.Loaded(psycholog),
            awaria,
            awaria,
            CurrentUserRead.Absent
        )

        assertEquals(
            listOf(null, psycholog, null),
            odczyty.lastKnownUser().toList()
        )
    }
}
