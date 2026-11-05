package com.example.stellare.fakes

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.remote.FirestoreCollections
import com.example.stellare.data.remote.SettlementWrite
import com.example.stellare.fakes.EscrowTestWorld.Companion.contactWrite
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakePrivateDocumentFidelityTest {

    private suspend fun denial(block: suspend () -> Unit): String {
        try {
            block()
        } catch (e: IllegalStateException) {
            return e.message.orEmpty()
        }
        fail("operacja miała zostać odrzucona")
        throw AssertionError()
    }

    @Test
    fun `zapis salda do dokumentu publicznego jest odrzucany przez atrape tak jak przez regule`() = runTest {
        val w = EscrowTestWorld()

        val message = denial {
            w.attack("k1", listOf(SettlementWrite.Update(FirestoreCollections.USERS, "k1", mapOf("coinsBalance" to 99_999))))
        }

        assertTrue(message, message.startsWith("PERMISSION_DENIED"))
        assertEquals(200, w.balance("k1"))
    }

    @Test
    fun `zapis do dokumentu prywatnego, ktory nie istnieje, konczy sie NOT_FOUND`() = runTest {
        val w = EscrowTestWorld()
        w.deleteAccount("k1")

        val message = denial { w.attack("k1", listOf(contactWrite("k1", mapOf("coinsBalance" to 10)))) }

        assertTrue(message, message.contains("NOT_FOUND"))
    }

    @Test
    fun `pole spoza dokumentu prywatnego nie przechodzi`() = runTest {
        val w = EscrowTestWorld()

        val message = denial { w.attack("k1", listOf(contactWrite("k1", mapOf("bonus" to 10)))) }

        assertTrue(message, message.startsWith("PERMISSION_DENIED"))
    }

    @Test
    fun `przyrzad pomiarowy nie pomija po cichu konta bez dokumentu prywatnego`() {
        val w = EscrowTestWorld()
        w.users.privateProfiles.profiles.value = w.users.privateProfiles.profiles.value - "k2"

        val failure = try {
            w.coinsInSystem()
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertTrue(
            "suma monet zaniżona po cichu o saldo konta bez dokumentu prywatnego uczyniłaby asercje trywialnymi",
            failure != null
        )
    }

    @Test
    fun `przyrzad pomiarowy widzi dokladnie zasiane monety`() {
        val w = EscrowTestWorld(patients = mapOf("k1" to 200, "k2" to 100), psychologists = mapOf("p1" to 50))

        assertEquals(350, w.coinsInSystem())
        assertEquals(100, w.balance("k2"))
    }

    @Test
    fun `zasiane konta wszystkich atrap maja dokument prywatny, a saldo z balances`() {
        val users = FakeUserDataSource(listOf(UserModel(id = "a"), UserModel(id = "b")), balances = mapOf("b" to 15))

        assertEquals(0, users.balanceOf("a"))
        assertEquals(15, users.balanceOf("b"))
        assertEquals(null, users.balanceOf("c"))
    }
}
