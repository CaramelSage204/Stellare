package com.example.stellare.data

import com.example.stellare.data.model.RatingModel
import com.example.stellare.data.model.ReviewModel
import com.example.stellare.data.remote.DocumentWrite
import com.example.stellare.data.remote.FirestoreReviewDataSource
import com.example.stellare.data.remote.RatingTransaction
import com.example.stellare.data.remote.TransactionRunner
import com.example.stellare.data.remote.reviewWithRatingWrites
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewWritePlanTest {

    private val review = ReviewModel(
        reviewId = "psych1_pacjent1", psychologistId = "psych1", reviewerId = "pacjent1", rating = 5
    )

    private class RecordingRunner(private val existing: RatingModel) : TransactionRunner {
        val commits = mutableListOf<List<DocumentWrite>>()
        val readIds = mutableListOf<String>()

        override suspend fun <T> run(block: (RatingTransaction) -> T): T = block(
            object : RatingTransaction {
                override fun readRating(psychologistId: String): RatingModel {
                    readIds += psychologistId
                    return existing
                }

                override fun write(writes: List<DocumentWrite>) {
                    commits += writes
                }
            }
        )
    }

    @Test
    fun `recenzja i agregat ida w jednym zatwierdzeniu`() = runTest {
        val runner = RecordingRunner(RatingModel(8, 2))
        FirestoreReviewDataSource(runner).saveReviewWithRating(review, "psych1")

        assertEquals(
            "recenzja i agregat muszą trafić do jednej partii, inaczej przy awarii zostaje recenzja bez oceny i ocena nie da się już zaliczyć",
            1,
            runner.commits.size
        )
        assertEquals(2, runner.commits.single().size)
    }

    @Test
    fun `agregat jest liczony z wartosci odczytanej w tej samej transakcji`() = runTest {
        val runner = RecordingRunner(RatingModel(8, 2))
        FirestoreReviewDataSource(runner).saveReviewWithRating(review, "psych1")

        assertEquals(listOf("psych1"), runner.readIds)
        val agregat = runner.commits.single().single { it.collection == "ratings" }
        assertEquals(mapOf("sum" to 13, "count" to 3), agregat.data)
    }

    @Test
    fun `agregat laduje w kolekcji ratings a nie w dokumencie uzytkownika`() {
        val zapisy = reviewWithRatingWrites(review, "psych1", 13, 3)

        assertTrue("ocena wróciłaby do users i dała się wyzerować", zapisy.none { it.collection == "users" })
        val agregat = zapisy.single { it.collection == "ratings" }
        assertEquals("psych1", agregat.id)
        assertEquals(mapOf("sum" to 13, "count" to 3), agregat.data)
    }

    @Test
    fun `agregat jest zapisem z merge zeby pierwsza ocena tworzyla dokument a kolejne go aktualizowaly`() {
        val agregat = reviewWithRatingWrites(review, "psych1", 5, 1).single { it.collection == "ratings" }
        assertTrue("bez merge kolejna ocena nadpisałaby dokument agregatu jako nowy", agregat.merge)
    }

    @Test
    fun `zapis recenzji niesie przekazany obiekt recenzji pod jej wlasnym reviewId bez scalania`() {
        val recenzja = reviewWithRatingWrites(review, "psych1", 5, 1).single { it.collection == "reviews" }
        assertEquals(review.reviewId, recenzja.id)
        assertEquals(review, recenzja.data)
        assertEquals(false, recenzja.merge)
    }
}
