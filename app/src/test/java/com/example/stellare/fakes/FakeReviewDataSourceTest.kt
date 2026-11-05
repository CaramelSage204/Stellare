package com.example.stellare.fakes

import com.example.stellare.data.model.RatingModel
import com.example.stellare.data.model.ReviewModel
import com.example.stellare.data.remote.reviewWithRatingWrites
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeReviewDataSourceTest {

    private val ratings = FakeRatingDataSource()
    private val source = FakeReviewDataSource(ratings).apply {
        completions += "psych1_pacjent1"
        completions += "psych1_pacjent2"
    }
    private val review = ReviewModel(
        reviewId = "psych1_pacjent1", psychologistId = "psych1", reviewerId = "pacjent1", rating = 5, comment = "ok"
    )

    private suspend fun assertDenied(reason: String, message: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: IllegalStateException) {
            assertTrue("odmowa z innego powodu niż \"$reason\": ${e.message}", e.message.orEmpty().contains(reason))
            return
        }
        fail(message)
    }

    @Test
    fun `pierwsza ocena tworzy agregat z jedna ocena`() = runTest {
        source.saveReviewWithRating(review, "psych1")
        assertEquals(RatingModel(5, 1), ratings.ratings.value["psych1"])
    }

    @Test
    fun `ta sama recenzja nie liczy sie drugi raz`() = runTest {
        source.saveReviewWithRating(review, "psych1")
        assertDenied("review already exists", "druga recenzja tej samej pary musi zostać odrzucona jak w regułach") {
            source.saveReviewWithRating(review, "psych1")
        }
        assertEquals(RatingModel(5, 1), ratings.ratings.value["psych1"])
    }

    @Test
    fun `bez odbytej wizyty recenzja i agregat nie powstaja`() = runTest {
        val bezWizyty = FakeReviewDataSource(ratings)
        assertDenied("no completed session", "recenzja bez odbytej wizyty musi zostać odrzucona") {
            bezWizyty.saveReviewWithRating(review, "psych1")
        }
        assertNull(ratings.ratings.value["psych1"])
        assertEquals(emptyList<ReviewModel>(), bezWizyty.reviews.value)
    }

    @Test
    fun `psycholog nie ocenia samego siebie`() = runTest {
        val wlasna = review.copy(reviewId = "psych1_psych1", reviewerId = "psych1")
        source.completions += "psych1_psych1"
        assertDenied("cannot rate self", "psycholog nie może podbić własnej oceny") {
            source.saveReviewWithRating(wlasna, "psych1")
        }
        assertNull(ratings.ratings.value["psych1"])
    }

    @Test
    fun `ocena spoza zakresu jeden do piec jest odrzucona`() = runTest {
        listOf(0, 6).forEach { zla ->
            assertDenied("rating must be 1-5", "ocena $zla musi zostać odrzucona") {
                source.saveReviewWithRating(review.copy(rating = zla), "psych1")
            }
        }
        assertNull(ratings.ratings.value["psych1"])
        assertEquals(emptyList<ReviewModel>(), source.reviews.value)
    }

    @Test
    fun `agregat jest czytany przez szew transakcji a nie z nasluchu wiec nieaktualny odczyt nie konczy sie bledem`() = runTest {
        ratings.ratings.value = mapOf("psych1" to RatingModel(8, 2))
        source.transactions.staleReads.add(RatingModel(3, 1))

        source.saveReviewWithRating(review, "psych1")

        assertEquals(RatingModel(13, 3), ratings.ratings.value["psych1"])
        assertEquals(2, source.transactions.attempts)
    }

    @Test
    fun `dwie oceny tego samego psychologa naraz sumuja sie obie i zadna nie ginie`() = runTest {
        ratings.ratings.value = mapOf("psych1" to RatingModel(8, 2))
        val druga = ReviewModel(
            reviewId = "psych1_pacjent2", psychologistId = "psych1", reviewerId = "pacjent2", rating = 3
        )
        source.transactions.beforeFirstCommit = { source.saveReviewWithRating(druga, "psych1") }

        source.saveReviewWithRating(review, "psych1")

        assertEquals(RatingModel(16, 4), ratings.ratings.value["psych1"])
        assertEquals(setOf("psych1_pacjent1", "psych1_pacjent2"), source.reviews.value.map { it.reviewId }.toSet())
    }

    private suspend fun writeAggregate(sum: Int, count: Int) {
        source.transactions.run { transaction ->
            transaction.write(reviewWithRatingWrites(review, "psych1", sum, count))
        }
    }

    @Test
    fun `agregat rozjechany z ocena odrzuca cala partie razem z recenzja`() = runTest {
        assertDenied("sum must grow", "suma niezgodna z oceną musi zostać odrzucona") { writeAggregate(500, 1) }
        assertEquals(emptyList<ReviewModel>(), source.reviews.value)
        assertNull(ratings.ratings.value["psych1"])
    }

    @Test
    fun `licznik podbity o dwa jest odrzucony`() = runTest {
        assertDenied("count must grow", "licznik musi rosnąć o dokładnie jeden") { writeAggregate(5, 2) }
        assertEquals(emptyList<ReviewModel>(), source.reviews.value)
    }

    @Test
    fun `zapis agregatu bez recenzji w tym samym zatwierdzeniu jest odrzucony`() = runTest {
        assertDenied("one commit", "agregat bez recenzji musi zostać odrzucony") {
            source.transactions.run { transaction ->
                transaction.write(reviewWithRatingWrites(review, "psych1", 5, 1).filter { it.collection == "ratings" })
            }
        }
        assertNull(ratings.ratings.value["psych1"])
    }
}
