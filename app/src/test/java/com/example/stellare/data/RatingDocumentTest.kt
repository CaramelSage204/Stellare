package com.example.stellare.data

import com.example.stellare.data.model.RatingModel
import com.example.stellare.data.remote.FirestoreCollections
import com.google.firebase.firestore.util.CustomClassMapper
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingDocumentTest {

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `dokument agregatu ma dokladnie pola sum i count ktorych wymagaja reguly`() {
        val dokument = CustomClassMapper.convertToPlainJavaTypes(RatingModel(sum = 13, count = 3)) as Map<String, Any?>
        assertEquals(
            "reguła ratings przyjmuje wyłącznie sum i count; inny zestaw pól odrzuci każdą ocenę",
            mapOf<String, Any?>("sum" to 13, "count" to 3),
            dokument
        )
    }

    @Test
    fun `nazwy kolekcji w kliencie sa tymi samymi, ktore chronia reguly`() {
        val reguly = File("../firebase/firestore.rules").readText()
        assertTrue(
            "klient zapisuje agregat do kolekcji, której reguły nie znają: odrzucone zostaną wszystkie oceny po pierwszej",
            reguly.contains("match /${FirestoreCollections.RATINGS}/{")
        )
        assertTrue(reguly.contains("match /${FirestoreCollections.REVIEWS}/{"))
    }

    @Test
    fun `kod dostepu do danych nie zawiera literalow nazw kolekcji ocen i recenzji`() {
        val literal = Regex("""collection\("(rating|ratings|review|reviews)"\)""")
        val naruszenia = File("src/main/java/com/example/stellare/data/remote")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { literal.containsMatchIn(it.readText()) }
            .map { it.name }
            .toList()
        assertEquals(
            "literał nazwy kolekcji obok stałej pozwala na literówkę, której nie wykryje żaden test warstwy poniżej szwu",
            emptyList<String>(),
            naruszenia
        )
    }
}
