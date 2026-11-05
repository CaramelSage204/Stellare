package com.example.stellare.model

import com.example.stellare.data.model.RatingModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RatingModelTest {

    @Test
    fun `brak ocen daje zero a nie NaN ktore trafiloby na ekran jako NaN`() {
        val srednia = RatingModel(sum = 0, count = 0).average
        assertFalse("średnia bez ocen nie może być NaN, bo użytkownik zobaczy napis NaN", srednia.isNaN())
        assertEquals(0.0, srednia, 0.0)
        assertEquals("NaN w %.1f to napis NaN", "0.0", "%.1f".format(java.util.Locale.US, srednia))
    }

    @Test
    fun `srednia to suma przez licznik`() {
        assertEquals(4.0, RatingModel(12, 3).average, 0.0)
        assertEquals(3.5, RatingModel(7, 2).average, 0.0)
    }

    @Test
    fun `dolozenie oceny podnosi sume o ocene a licznik o jeden`() {
        assertEquals(RatingModel(sum = 13, count = 3), RatingModel(sum = 8, count = 2).plus(5))
        assertEquals(RatingModel(sum = 5, count = 1), RatingModel().plus(5))
    }

    @Test
    fun `domyslny model to brak ocen`() {
        assertEquals(RatingModel(0, 0), RatingModel())
    }
}
