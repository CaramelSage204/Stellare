package com.example.stellare.psychologistdetail

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.ReviewModel
import com.example.stellare.ui.psychologistdetail.ReviewFooter
import com.example.stellare.ui.psychologistdetail.ReviewSummary
import com.example.stellare.ui.psychologistdetail.ReviewsLoad
import com.example.stellare.ui.psychologistdetail.bookingDateLabel
import com.example.stellare.ui.psychologistdetail.canAffordBooking
import com.example.stellare.ui.psychologistdetail.hasUnpricedSlots
import com.example.stellare.ui.psychologistdetail.headerRatingText
import com.example.stellare.ui.psychologistdetail.priceLabel
import com.example.stellare.ui.psychologistdetail.ratingCaption
import com.example.stellare.ui.psychologistdetail.reviewFooter
import com.example.stellare.ui.psychologistdetail.slotOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailPresentationTest {

    private fun slot(id: String = "a1", price: Int = 50, date: String = "2026-11-05", time: String = "14:30") =
        AppointmentModel(appointmentId = id, date = date, time = time, priceCoins = price)

    private fun review(rating: Int) = ReviewModel(rating = rating)

    @Test
    fun `termin z cena jest wybieralny i ma etykiete z data i godzina`() {
        val option = slotOption(slot(), selectedId = null)
        assertTrue(option.isBookable)
        assertEquals("05.11 o 14:30", option.label)
    }

    @Test
    fun `termin bez ceny jest niewybieralny i opisany`() {
        val option = slotOption(slot(price = 0), selectedId = null)
        assertFalse(option.isBookable)
        assertEquals("05.11 o 14:30 (bez ceny)", option.label)
    }

    @Test
    fun `termin z ujemna cena tez jest niewybieralny`() {
        assertFalse(slotOption(slot(price = -5), selectedId = null).isBookable)
    }

    @Test
    fun `termin bez ceny nigdy nie jest zaznaczony nawet z pasujacym identyfikatorem`() {
        assertFalse(slotOption(slot(id = "x", price = 0), selectedId = "x").isSelected)
    }

    @Test
    fun `termin z cena jest zaznaczony tylko gdy identyfikator pasuje`() {
        assertTrue(slotOption(slot(id = "x"), selectedId = "x").isSelected)
        assertFalse(slotOption(slot(id = "x"), selectedId = "y").isSelected)
        assertFalse(slotOption(slot(id = "x"), selectedId = null).isSelected)
    }

    @Test
    fun `data w nieoczekiwanym formacie jest pokazana bez zmian`() {
        assertEquals("jutro o 14:30", slotOption(slot(date = "jutro"), selectedId = null).label)
    }

    @Test
    fun `data rezerwacji w pelnym formacie dzien kropka miesiac kropka rok`() {
        assertEquals("05.11.2026", bookingDateLabel("2026-11-05"))
        assertEquals("niepoprawna", bookingDateLabel("niepoprawna"))
    }

    @Test
    fun `cena w jednostce WKT na sesje`() {
        assertEquals("120 WKT / sesja", priceLabel(120.0))
        assertEquals("0 WKT / sesja", priceLabel(0.0))
        assertEquals("99 WKT / sesja", priceLabel(99.9))
    }

    @Test
    fun `naglowek pokazuje srednia i liczbe glosow tylko dla prawdziwych opinii`() {
        val summary = ReviewSummary.fromLoad(ReviewsLoad.Loaded(listOf(review(5), review(4))))
        assertTrue(Regex("4[.,]5 [(]2[)]").matches(headerRatingText(summary)))
    }

    @Test
    fun `naglowek rozroznia wczytywanie pustke i niepowodzenie`() {
        assertEquals("Wczytywanie…", headerRatingText(ReviewSummary.fromLoad(null)))
        assertEquals("Brak ocen", headerRatingText(ReviewSummary.fromLoad(ReviewsLoad.Loaded(emptyList()))))
        assertEquals("Oceny niedostępne", headerRatingText(ReviewSummary.fromLoad(ReviewsLoad.Failed("blad"))))
    }

    @Test
    fun `podpis sekcji opinii rozroznia trzy stany`() {
        assertEquals("2 opinie", ratingCaption(ReviewSummary.fromLoad(ReviewsLoad.Loaded(listOf(review(5), review(4))))))
        assertEquals("Wczytywanie…", ratingCaption(ReviewSummary.fromLoad(null)))
        assertEquals("Brak opinii", ratingCaption(ReviewSummary.fromLoad(ReviewsLoad.Loaded(emptyList()))))
        assertEquals("Brak połączenia", ratingCaption(ReviewSummary.fromLoad(ReviewsLoad.Failed("Brak połączenia"))))
    }

    @Test
    fun `pusta lista nie podpisuje sie brakiem opinii w trakcie wczytywania`() {
        assertFalse(ratingCaption(ReviewSummary.fromLoad(null)) == "Brak opinii")
    }

    @Test
    fun `wlasna opinia wypiera formularz`() {
        assertEquals(ReviewFooter.OwnReview, reviewFooter(hasOwnReview = true, hasCompletedSession = true))
        assertEquals(ReviewFooter.OwnReview, reviewFooter(hasOwnReview = true, hasCompletedSession = false))
    }

    @Test
    fun `formularz tylko po odbytej wizycie i bez wlasnej opinii`() {
        assertEquals(ReviewFooter.Composer, reviewFooter(hasOwnReview = false, hasCompletedSession = true))
        assertEquals(ReviewFooter.Nothing, reviewFooter(hasOwnReview = false, hasCompletedSession = false))
    }

    @Test
    fun `wyjasnienie pod lista tylko gdy jest termin bez ceny`() {
        assertTrue(hasUnpricedSlots(listOf(slot(), slot(price = 0))))
        assertFalse(hasUnpricedSlots(listOf(slot(), slot(price = 10))))
        assertFalse(hasUnpricedSlots(emptyList()))
    }

    @Test
    fun `stac na rezerwacje gdy saldo rowne cenie lub wieksze`() {
        assertTrue(canAffordBooking(balance = 50, cost = 50))
        assertTrue(canAffordBooking(balance = 51, cost = 50))
        assertFalse(canAffordBooking(balance = 49, cost = 50))
    }
}
