package com.example.stellare.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Szew, którego nie da się przejść testem jednostkowym: mapowanie snapshotu Firestore
 * na model żyje w callbacku SDK, a DocumentSnapshot nie ma publicznego konstruktora.
 * Dlatego niezmiennik jest pilnowany na źródle produkcyjnym — tak samo jak zakaz
 * literałów nazw kolekcji w [RatingDocumentTest].
 *
 * CZEGO TEN TEST NIE WYKRYWA: jest zapadką na kształt kodu, nie sprawdzeniem
 * zachowania. Widzi, że pole tożsamości jest nadpisywane przy każdym odwzorowaniu
 * modelu — ale NIE widzi, CZYM jest nadpisywane, więc przejdzie kod, który wpisze
 * tam z powrotem wartość z treści dokumentu (`copy(id = document.getString("id"))`)
 * albo nadpisze ją ponownie w kolejnym wyrażeniu. Dowodem na to, że mapowanie działa,
 * są mutacje M7/M8 i R1-M7/M8 z raportu, nie ten test.
 */
class DocumentIdentityTest {

    private data class Mapowanie(val plik: String, val model: String, val pole: String)

    private val mapowania = listOf(
        Mapowanie("FirestorePostDataSource.kt", "PostModel", "id"),
        Mapowanie("FirestoreChatDataSource.kt", "ChatModel", "chatId"),
        Mapowanie("FirestoreMessageDataSource.kt", "MessageModel", "messageId"),
        Mapowanie("FirestoreAppointmentDataSource.kt", "AppointmentModel", "appointmentId"),
        Mapowanie("FirestoreNoteDataSource.kt", "NoteModel", "noteId"),
        Mapowanie("FirestoreReviewDataSource.kt", "ReviewModel", "reviewId"),
        Mapowanie("FirestoreUserDataSource.kt", "UserModel", "id"),
        Mapowanie("UserDocumentReader.kt", "UserModel", "id"),
        Mapowanie("SettlementTransaction.kt", "AppointmentModel", "appointmentId")
    )

    /**
     * Postać kanoniczna: bez komentarzy (komentarz z nazwą modelu nie jest odwzorowaniem),
     * bez białych znaków (łamanie wyrażenia na kilka linii, wcięcia i spacje wokół `=`
     * to legalne formatowanie) i ze skróconą formą ktx `toObject<X>()` rozwiniętą do
     * postaci jawnej. Bez tej normalizacji sam przebieg formattera zapalałby test.
     */
    private fun postacKanoniczna(tekst: String): String = tekst
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")
        .replace(Regex("""\s+"""), "")
        .replace(Regex("""toObject<(\w+)>\(\)""")) { "toObject(${it.groupValues[1]}::class.java)" }
        .replace(Regex("""toObjects<(\w+)>\(\)""")) { "toObjects(${it.groupValues[1]}::class.java)" }

    private fun zrodlo(plik: String): String {
        val file = File("src/main/java/com/example/stellare/data/remote/$plik")
        assertTrue(
            "nie ma pliku $plik — niezmiennik tożsamości dokumentu przestał być pilnowany na milcząco",
            file.isFile
        )
        return postacKanoniczna(file.readText())
    }

    private fun String.ile(fragment: String): Int = split(fragment).size - 1

    @Test
    fun `zadne zrodlo danych nie bierze tozsamosci hurtowo z pol dokumentow`() {
        mapowania.forEach { (plik, model, _) ->
            assertEquals(
                "$plik bierze tożsamość z pola dokumentu (toObjects): dwa dokumenty mogą twierdzić," +
                    " że są tym samym obiektem — zapis celuje wtedy w dokument, który nie istnieje," +
                    " odmowa reguł wraca do lokalnej kolejki mutacji i aplikacja wywraca się przy" +
                    " każdym starcie (zadanie 11, R1: konto do odratowania tylko przez wyczyszczenie" +
                    " danych aplikacji)",
                0,
                zrodlo(plik).ile("toObjects($model::class.java)")
            )
        }
    }

    // Liczone jest KAZDE uzycie `<model>::class.java`, nie tylko te w mapowaniu.
    // Osobne zliczanie samego mapowania przepuscilo mutanta: po przywroceniu
    // toObjects() w nasluchach funkcja mapujaca zostawala w pliku jako martwy kod
    // i wiazala sie ze wzorcem, choc nic nie wiazalo nasluchow z nia.
    @Test
    fun `kazde odwzorowanie modelu nadpisuje pole tozsamosci`() {
        mapowania.forEach { (plik, model, pole) ->
            val tekst = zrodlo(plik)
            val wszystkieUzycia = tekst.ile("$model::class.java")
            val zNadpisaniem = tekst.ile("toObject($model::class.java)?.copy($pole=")
            assertTrue(
                "$plik nie odwzorowuje już $model w ogóle — niezmiennik tożsamości nie ma czego pilnować",
                wszystkieUzycia > 0
            )
            assertEquals(
                "$plik odwzorowuje $model bez copy($pole = …): pole pisane przez klienta zostaje" +
                    " tożsamością obiektu, więc duplikat klucza znów jest możliwy, a zapis i usunięcie" +
                    " celują w dokument o identyfikatorze wziętym z treści dokumentu",
                wszystkieUzycia,
                zNadpisaniem
            )
        }
    }
}
