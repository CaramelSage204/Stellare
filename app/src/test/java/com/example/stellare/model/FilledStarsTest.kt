package com.example.stellare.model

import com.example.stellare.data.model.MAX_STARS
import com.example.stellare.data.model.filledStars
import org.junit.Assert.assertEquals
import org.junit.Test

class FilledStarsTest {

    @Test
    fun `srednia cztery daje cztery gwiazdki`() =
        assertEquals("średnia 4,0 ma dać cztery pełne gwiazdki, nie pięć", 4, filledStars(4.0))

    @Test
    fun `zero daje zero`() =
        assertEquals("średnia 0 ma dać zero pełnych gwiazdek", 0, filledStars(0.0))

    @Test
    fun `piec daje piec`() =
        assertEquals("średnia 5,0 ma dać pięć pełnych gwiazdek", 5, filledStars(5.0))

    @Test
    fun `jedynka daje jedna`() =
        assertEquals("średnia 1,0 ma dać jedną pełną gwiazdkę", 1, filledStars(1.0))

    @Test
    fun `ulamek zaokraglony w dol do pelnej gwiazdki`() {
        assertEquals("4,5 to jeszcze cztery pełne gwiazdki", 4, filledStars(4.5))
        assertEquals("4,4 to cztery pełne gwiazdki", 4, filledStars(4.4))
        assertEquals("3,2 to trzy pełne gwiazdki", 3, filledStars(3.2))
    }

    @Test
    fun `ulamek pokazany jako cztery i zero daje cztery`() {
        assertEquals("3,96 pokazuje się jako 4,0 więc ma mieć cztery gwiazdki", 4, filledStars(3.96))
        assertEquals("3,94 pokazuje się jako 3,9 więc ma mieć trzy gwiazdki", 3, filledStars(3.94))
    }

    @Test
    fun `wartosci poza zakresem sa przycinane`() {
        assertEquals("ujemna średnia ma dać zero", 0, filledStars(-1.0))
        assertEquals("średnia powyżej pięciu ma dać pięć", MAX_STARS, filledStars(7.3))
        assertEquals("brak liczby ma dać zero", 0, filledStars(Double.NaN))
    }
}
