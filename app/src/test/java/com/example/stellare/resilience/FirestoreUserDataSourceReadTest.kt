package com.example.stellare.resilience

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.remote.FirestoreUserDataSource
import com.example.stellare.data.remote.UserDocumentReader
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testuje **produkcyjny** [FirestoreUserDataSource], nie atrapę: w pamięci jest tylko odczyt
 * dokumentu, a cała polityka błędu („`null` znaczy brak dokumentu, awaria wychodzi wyjątkiem")
 * wykonuje się z produkcyjnego kodu.
 *
 * Czego ten plik **nie** dowodzi: tożsamości profilu. `copy(id = uid)` przeniosło się szwem do
 * [com.example.stellare.data.remote.FirestoreUserDocumentReader], którego te testy podmieniają,
 * więc jego usunięcie nie zabiłoby żadnego z nich — asercja na identyfikatorze poniżej sprawdza
 * tylko, że źródło danych oddaje to, co dał czytnik, bez podmiany. Linii w czytniku pilnuje
 * zapadka [com.example.stellare.data.DocumentIdentityTest] na kształcie źródła.
 */
class FirestoreUserDataSourceReadTest {

    private class Documents(
        private val stored: Map<String, UserModel> = emptyMap(),
        private val failure: Throwable? = null
    ) : UserDocumentReader {
        var reads = 0
            private set

        override suspend fun read(uid: String): UserModel? {
            reads++
            failure?.let { throw it }
            return stored[uid]
        }
    }

    private fun odmowa() =
        FirebaseFirestoreException("odmowa regul", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    @Test
    fun `nieudany odczyt wychodzi wyjatkiem, a nie null-em udajacym brak konta`() = runTest {
        val source = FirestoreUserDataSource(Documents(failure = odmowa()))

        val wynik = runCatching { source.getUserOnce("p1") }

        assertTrue(
            "nieudany odczyt zamieniony na null jest nieodróżnialny od nieistniejącego konta: " +
                "stan NameLookup.Unavailable staje się wtedy nieosiągalny w produkcji, a przycisk " +
                "ponowienia w portfelu martwym kodem. Wynik: $wynik",
            wynik.exceptionOrNull() is FirebaseFirestoreException
        )
    }

    @Test
    fun `brak dokumentu to null, nie wyjatek`() = runTest {
        val source = FirestoreUserDataSource(Documents(stored = emptyMap()))

        assertNull(
            "nieistniejący dokument to pustka, nie awaria: inaczej portfel pokazywałby przycisk " +
                "ponowienia dla konta, którego nikt nigdy nie odzyska",
            source.getUserOnce("nikogo-takiego")
        )
    }

    @Test
    fun `odczytany dokument wraca tym, co dal czytnik`() = runTest {
        val source = FirestoreUserDataSource(
            Documents(stored = mapOf("p1" to UserModel(id = "p1", firstName = "Anna", lastName = "Nowak")))
        )

        assertEquals("Anna", source.getUserOnce("p1")?.firstName)
        assertEquals("p1", source.getUserOnce("p1")?.id)
    }

    @Test
    fun `pusty identyfikator nie pyta serwera i daje null`() = runTest {
        val documents = Documents(failure = odmowa())
        val source = FirestoreUserDataSource(documents)

        assertNull("pusty uid to brak konta, nie zapytanie do serwera", source.getUserOnce(""))
        assertEquals("pusty uid nie może wywołać odczytu dokumentu", 0, documents.reads)
    }
}
