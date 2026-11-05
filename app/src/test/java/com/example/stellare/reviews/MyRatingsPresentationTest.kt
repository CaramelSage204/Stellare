package com.example.stellare.reviews

import com.example.stellare.data.model.RatingModel
import com.example.stellare.ui.reviews.myRatingAverageText
import com.example.stellare.ui.reviews.myRatingBasisText
import org.junit.Assert.assertEquals
import org.junit.Test

class MyRatingsPresentationTest {

    @Test
    fun `konto bez dokumentu ratings nie pokazuje 0,0 tylko brak oceny`() {
        assertEquals(
            "karta nie może pokazywać średniej 0,0 dla psychologa, który po prostu nie ma jeszcze agregatu",
            "–",
            myRatingAverageText(RatingModel())
        )
        assertEquals("Brak ocen", myRatingBasisText(RatingModel()))
    }

    @Test
    fun `srednia i liczba opinii pochodza z tego samego agregatu`() {
        val agregat = RatingModel(sum = 45, count = 10)
        assertEquals("4,5", myRatingAverageText(agregat))
        assertEquals("Na podstawie 10 opinii pacjentów", myRatingBasisText(agregat))
    }

    @Test
    fun `jedna opinia daje poprawna forme liczby`() {
        assertEquals("Na podstawie 1 opinii pacjenta", myRatingBasisText(RatingModel(sum = 5, count = 1)))
    }
}
