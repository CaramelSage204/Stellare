package com.example.stellare.account

import com.example.stellare.data.model.AccountDeletionPlan
import com.example.stellare.data.model.DeletionStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionPlanTest {

    private val plan = AccountDeletionPlan.build(
        uid = "u1",
        posts = listOf(post("p1"), post("p2")),
        notes = listOf(note("n1"), note("n2"))
    )

    @Test
    fun `konto w Auth jest usuwane jako ostatni krok`() {
        assertTrue(
            "konto w Auth musi byc ostatnim krokiem, bo po nim token przestaje dzialac",
            plan.last() == DeletionStep.DeleteAuthAccount
        )
    }

    @Test
    fun `konto w Auth wystepuje dokladnie raz`() {
        assertEquals("konto w Auth ma byc usuniete raz", 1, plan.count { it == DeletionStep.DeleteAuthAccount })
    }

    @Test
    fun `dokument uzytkownika jest usuwany przed ogloszeniami`() {
        val userIndex = plan.indexOf(DeletionStep.DeleteUserDocument)
        val firstPostIndex = plan.indexOfFirst { it is DeletionStep.DeletePost }
        assertTrue(
            "regula pozwala skasowac ogloszenie dopiero po usunieciu dokumentu uzytkownika",
            userIndex in 0 until firstPostIndex
        )
    }

    @Test
    fun `dokument prywatny z telefonem i e-mailem jest usuwany tuz przed publicznym, nigdy po nim`() {
        val privateIndex = plan.indexOf(DeletionStep.DeletePrivateProfile)
        val userIndex = plan.indexOf(DeletionStep.DeleteUserDocument)
        assertEquals("plan musi zawierać usunięcie dokumentu prywatnego dokładnie raz", 1, plan.count { it == DeletionStep.DeletePrivateProfile })
        assertTrue(
            "awaria po usunięciu dokumentu publicznego zostawiłaby sierocy dokument z numerem telefonu i adresem e-mail, " +
                "którego nikt już nie wyświetli ani nie posprząta",
            privateIndex in 0 until userIndex
        )
        assertEquals("nic nie wolno wcisnąć między dokumenty: okno przerwania ma być jak najkrótsze", userIndex, privateIndex + 1)
    }

    @Test
    fun `notatki sa usuwane przed dokumentem uzytkownika`() {
        val userIndex = plan.indexOf(DeletionStep.DeleteUserDocument)
        val lastNoteIndex = plan.indexOfLast { it is DeletionStep.DeleteNote }
        assertTrue("notatki kliniczne maja zniknac, gdy konto jest jeszcze w pelni sprawne", lastNoteIndex < userIndex)
    }

    @Test
    fun `plan zawiera wszystkie ogloszenia i notatki wlasciciela`() {
        assertEquals(
            listOf<DeletionStep>(
                DeletionStep.DeleteNote("n1"),
                DeletionStep.DeleteNote("n2"),
                DeletionStep.DeletePrivateProfile,
                DeletionStep.DeleteUserDocument,
                DeletionStep.DeletePost("p1"),
                DeletionStep.DeletePost("p2"),
                DeletionStep.DeleteAuthAccount
            ),
            plan
        )
    }

    @Test
    fun `cudze ogloszenia nie trafiaja do planu`() {
        val result = AccountDeletionPlan.build("u1", listOf(post("p1"), post("obce", authorId = "u2")), emptyList())
        assertTrue("cudze ogloszenie nie moze trafic do usuniecia", DeletionStep.DeletePost("obce") !in result)
    }

    @Test
    fun `notatka cudzego autora nie trafia do planu a stara bez autora tak`() {
        val result = AccountDeletionPlan.build(
            "u1",
            emptyList(),
            listOf(note("obca", authorId = "pacjent"), note("stara", authorId = ""))
        )
        assertTrue("notatka innego autora zostaje", DeletionStep.DeleteNote("obca") !in result)
        assertTrue("notatka bez autora to notatka kliniczna psychologa", DeletionStep.DeleteNote("stara") in result)
    }

    @Test
    fun `bez ogloszen i notatek plan to dokument uzytkownika i konto`() {
        assertEquals(
            listOf<DeletionStep>(DeletionStep.DeletePrivateProfile, DeletionStep.DeleteUserDocument, DeletionStep.DeleteAuthAccount),
            AccountDeletionPlan.build("u1", emptyList(), emptyList())
        )
    }

    private val fullPlan = AccountDeletionPlan.build(
        uid = "u1",
        posts = listOf(post("p1")),
        notes = listOf(note("n1")),
        appointments = listOf(slot("w1"), slot("w2")),
        hasVerificationRequest = true
    )

    @Test
    fun `wolne terminy sa usuwane przed dokumentem uzytkownika`() {
        val userIndex = fullPlan.indexOf(DeletionStep.DeleteUserDocument)
        val lastSlotIndex = fullPlan.indexOfLast { it is DeletionStep.DeleteFreeSlot }
        assertTrue(
            "wolne terminy maja zniknac, gdy konto jest jeszcze w pelni sprawne, a nie dopiero po profilu",
            lastSlotIndex in 0 until userIndex
        )
    }

    @Test
    fun `zgloszenie weryfikacyjne jest usuwane po dokumencie uzytkownika`() {
        val userIndex = fullPlan.indexOf(DeletionStep.DeleteUserDocument)
        val requestIndex = fullPlan.indexOf(DeletionStep.DeleteVerificationRequest)
        assertTrue(
            "regula pozwala skasowac zgloszenie ze skanem dokumentu dopiero po usunieciu dokumentu uzytkownika",
            requestIndex > userIndex && userIndex >= 0
        )
    }

    @Test
    fun `konto w Auth zostaje ostatnie takze przy terminach i zgloszeniu`() {
        assertTrue("konto w Auth musi byc ostatnim krokiem", fullPlan.last() == DeletionStep.DeleteAuthAccount)
        assertEquals(1, fullPlan.count { it == DeletionStep.DeleteAuthAccount })
    }

    @Test
    fun `pelny plan ma ustalona kolejnosc`() {
        assertEquals(
            listOf<DeletionStep>(
                DeletionStep.DeleteNote("n1"),
                DeletionStep.DeleteFreeSlot("w1"),
                DeletionStep.DeleteFreeSlot("w2"),
                DeletionStep.DeletePrivateProfile,
                DeletionStep.DeleteUserDocument,
                DeletionStep.DeletePost("p1"),
                DeletionStep.DeleteVerificationRequest,
                DeletionStep.DeleteAuthAccount
            ),
            fullPlan
        )
    }

    @Test
    fun `zarezerwowane, odbyte, odwolane i cudze terminy nie trafiaja do planu`() {
        val result = AccountDeletionPlan.build(
            "u1",
            emptyList(),
            emptyList(),
            listOf(
                slot("zajety", status = "BOOKED"),
                slot("odbyty", status = "COMPLETED"),
                slot("odwolany", status = "CANCELLED"),
                slot("cudzy", psychologistId = "u2"),
                slot("wolny")
            )
        )
        assertEquals(
            "do usuniecia moze trafic wylacznie wlasny termin ze statusem FREE",
            listOf<DeletionStep>(
                DeletionStep.DeleteFreeSlot("wolny"),
                DeletionStep.DeletePrivateProfile,
                DeletionStep.DeleteUserDocument,
                DeletionStep.DeleteAuthAccount
            ),
            result
        )
    }

    @Test
    fun `bez zgloszenia plan nie probuje go usuwac`() {
        assertTrue(
            "krok zgloszenia bez zgloszenia to zbedny zapis",
            DeletionStep.DeleteVerificationRequest !in AccountDeletionPlan.build("u1", emptyList(), emptyList())
        )
    }
}
