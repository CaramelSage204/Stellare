package com.example.stellare.dashboard

import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.model.RatingModel
import com.example.stellare.ui.dashboard.accentedPostId
import com.example.stellare.ui.dashboard.priceChipText
import com.example.stellare.ui.dashboard.ratingBadgeText
import com.example.stellare.ui.dashboard.ratingBadges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostCardPresentationTest {

    @Test
    fun `brak opinii nie daje liczby na awatarze`() {
        assertNull("przy zerze opinii karta nie moze pokazac oceny", ratingBadgeText(RatingModel(0, 0)))
    }

    @Test
    fun `ocena pochodzi z sumy i liczby opinii a nie z wartosci zastepczej`() {
        assertEquals("średnia 9/2 ma dać 4,5", "4,5", ratingBadgeText(RatingModel(9, 2)))
        assertEquals("średnia 8/2 ma dać 4,0", "4,0", ratingBadgeText(RatingModel(8, 2)))
        assertEquals("średnia 5/1 ma dać 5,0", "5,0", ratingBadgeText(RatingModel(5, 1)))
    }

    @Test
    fun `mapa ocen pomija autorow bez opinii`() {
        val mapa = ratingBadges(
            mapOf(
                "a" to RatingModel(sum = 9, count = 2),
                "b" to RatingModel(sum = 0, count = 0)
            )
        )
        assertEquals("autor z opiniami ma mieć ocenę", "4,5", mapa["a"])
        assertTrue("autor bez opinii nie może trafić do mapy", "b" !in mapa)
    }

    @Test
    fun `akcent na ciemno dostaje pierwsze czyli najnowsze ogloszenie`() {
        val posty = listOf(PostModel(id = "nowe"), PostModel(id = "stare"))
        assertEquals("akcent ma paść na pierwszą kartę listy", "nowe", accentedPostId(posty))
    }

    @Test
    fun `pusta lista nie ma karty akcentowanej`() {
        assertNull("bez ogłoszeń nie ma czego akcentować", accentedPostId(emptyList()))
    }

    @Test
    fun `cena na karcie to cena z ogloszenia`() {
        val post = PostModel(type = PostType.OFFERING_THERAPY, priceCoins = 90)
        assertEquals("pigułka ma pokazać cenę zapisaną w ogłoszeniu", "90 WKT / sesja", priceChipText(post))
    }

    @Test
    fun `ogloszenie bez ceny nie ma pigulki`() {
        assertNull("cena zero nie może dać pigułki", priceChipText(PostModel(type = PostType.OFFERING_THERAPY, priceCoins = 0)))
    }

    @Test
    fun `ogloszenie pacjenta nie ma pigulki z cena`() {
        assertNull("ogłoszenie pacjenta nie pokazuje ceny", priceChipText(PostModel(type = PostType.SEEKING_PSYCHOLOGIST, priceCoins = 90)))
    }
}
