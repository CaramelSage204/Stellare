package com.example.stellare.model

import com.example.stellare.data.model.ReviewModel
import com.example.stellare.ui.psychologistdetail.ReviewSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSummaryTest {

    private fun review(rating: Int) = ReviewModel(reviewId = "r$rating", rating = rating)

    @Test
    fun `brak opinii daje zero i ukrywa histogram`() {
        val summary = ReviewSummary.from(emptyList())

        assertEquals(0, summary.count)
        assertEquals(0.0, summary.average, 0.0)
        assertFalse(summary.hasReviews)
        assertFalse(summary.showHistogram)
    }

    @Test
    fun `liczba opinii to liczba prawdziwych opinii a histogram sie pokazuje`() {
        val summary = ReviewSummary.from(listOf(review(5), review(4), review(5)))

        assertEquals(3, summary.count)
        assertTrue(summary.showHistogram)
    }

    @Test
    fun `histogram liczy opinie na gwiazdki a nie stale`() {
        val summary = ReviewSummary.from(listOf(review(5), review(4), review(5), review(1)))

        assertEquals(2, summary.countsByStars[5])
        assertEquals(1, summary.countsByStars[4])
        assertEquals(0, summary.countsByStars[3])
        assertEquals(0, summary.countsByStars[2])
        assertEquals(1, summary.countsByStars[1])
        assertEquals(0.5f, summary.shareOf(5), 0.0001f)
        assertEquals(0f, summary.shareOf(3), 0f)
    }

    @Test
    fun `srednia pochodzi z opinii`() {
        val summary = ReviewSummary.from(listOf(review(5), review(4)))

        assertEquals(4.5, summary.average, 0.0)
    }

    @Test
    fun `opinie spoza zakresu 1 do 5 sa ignorowane`() {
        val summary = ReviewSummary.from(listOf(review(0), review(7), review(3)))

        assertEquals(1, summary.count)
        assertEquals(3.0, summary.average, 0.0)
    }

    @Test
    fun `wczytywanie nie jest pustka`() {
        val loading = ReviewSummary.from(null)
        val empty = ReviewSummary.from(emptyList())

        assertTrue(loading.isLoading)
        assertFalse(loading.isEmpty)
        assertFalse(loading.hasReviews)
        assertFalse(loading.showHistogram)
        assertFalse(empty.isLoading)
        assertTrue(empty.isEmpty)
    }

    @Test
    fun `wczytane opinie nie sa ani wczytywaniem ani pustka`() {
        val summary = ReviewSummary.from(listOf(review(4)))

        assertFalse(summary.isLoading)
        assertFalse(summary.isEmpty)
        assertTrue(summary.hasReviews)
    }

    @Test
    fun `udzial przy braku opinii to zero bez dzielenia przez zero`() {
        assertEquals(0f, ReviewSummary.from(emptyList()).shareOf(5), 0f)
    }
}
