package com.example.stellare.profile

import com.example.stellare.ui.profile.profileReviewSummary
import com.example.stellare.ui.psychologistdetail.ReviewsLoad
import com.example.stellare.ui.psychologistdetail.headerRatingText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileReviewSummaryTest {

    @Test
    fun `specjalista przed zaladowaniem opinii widzi wczytywanie a nie brak wiersza`() {
        val summary = profileReviewSummary(true, null)
        assertNotNull("wiersz oceny ma byc widoczny przed zaladowaniem", summary)
        assertTrue("przed zaladowaniem stan to wczytywanie", summary!!.isLoading)
        assertEquals("Wczytywanie…", headerRatingText(summary))
    }

    @Test
    fun `specjalista z pusta lista widzi brak ocen`() {
        val summary = profileReviewSummary(true, ReviewsLoad.Loaded(emptyList()))
        assertEquals("Brak ocen", headerRatingText(summary!!))
    }

    @Test
    fun `specjalista po niepowodzeniu widzi oceny niedostepne`() {
        val summary = profileReviewSummary(true, ReviewsLoad.Failed("blad"))
        assertEquals("Oceny niedostępne", headerRatingText(summary!!))
    }

    @Test
    fun `pacjent nie ma wiersza oceny w zadnym stanie`() {
        assertNull("pacjent nie ma oceny przed zaladowaniem", profileReviewSummary(false, null))
        assertNull("pacjent nie ma oceny po zaladowaniu", profileReviewSummary(false, ReviewsLoad.Loaded(emptyList())))
    }
}
