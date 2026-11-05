package com.example.stellare.model

import com.example.stellare.data.model.averageRating
import org.junit.Assert.assertEquals
import org.junit.Test

class AverageRatingTest {

    @Test
    fun `zero glosow daje zero`() {
        assertEquals(0.0, averageRating(ratingSum = 0, ratingCount = 0), 0.0)
    }

    @Test
    fun `zero glosow daje zero rowniez przy niezerowej sumie`() {
        assertEquals(0.0, averageRating(ratingSum = 7, ratingCount = 0), 0.0)
    }

    @Test
    fun `trzy glosy po cztery daja cztery`() {
        assertEquals(4.0, averageRating(ratingSum = 12, ratingCount = 3), 0.0)
    }

    @Test
    fun `dzielenie nie jest calkowite`() {
        assertEquals(3.5, averageRating(ratingSum = 7, ratingCount = 2), 0.0)
    }

    @Test
    fun `duza historia nie traci dokladnosci`() {
        assertEquals(1.0, averageRating(ratingSum = 9998, ratingCount = 9998), 0.0)
    }
}
