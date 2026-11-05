package com.example.stellare.profile

import com.example.stellare.ui.profile.CustomPrices
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomPricesTest {

    @Test
    fun `nazwa uslugi z myslnikiem przechodzi odczyt`() {
        val prices = CustomPrices.parse("Terapia par - online - 150 WKT;Sesja audio - 80 WKT")

        assertEquals(listOf("Terapia par - online" to 150, "Sesja audio" to 80), prices.entries)
        assertEquals(emptyList<String>(), prices.unreadable)
    }

    @Test
    fun `nazwa uslugi z myslnikiem przechodzi odczyt i zapis bez utraty`() {
        val zapis = "Terapia par - online - 150 WKT;Sesja audio - 80 WKT"

        assertEquals(zapis, CustomPrices.parse(zapis).format())
    }

    @Test
    fun `nazwa z myslnikiem bez spacji przetrwa`() {
        val prices = CustomPrices.parse("Wideo-konsultacja - 90 WKT")

        assertEquals(listOf("Wideo-konsultacja" to 90), prices.entries)
    }

    @Test
    fun `wpis nieparsowalny nie znika przy zapisie`() {
        val prices = CustomPrices.parse("Sesja audio - 80 WKT;uszkodzony wpis;Wideo - dużo WKT")

        assertEquals(listOf("Sesja audio" to 80), prices.entries)
        assertEquals(listOf("uszkodzony wpis", "Wideo - dużo WKT"), prices.unreadable)
        assertEquals("Sesja audio - 80 WKT;uszkodzony wpis;Wideo - dużo WKT", prices.format())
    }

    @Test
    fun `dopisana stawka nie gubi wpisu nieparsowalnego`() {
        val prices = CustomPrices.parse("uszkodzony wpis")
        val zNowa = prices.copy(entries = prices.entries + ("Sesja audio" to 80))

        assertEquals("Sesja audio - 80 WKT;uszkodzony wpis", zNowa.format())
    }

    @Test
    fun `pusty zapis daje pusta liste`() {
        assertEquals(CustomPrices(emptyList(), emptyList()), CustomPrices.parse("  "))
        assertEquals(CustomPrices(emptyList(), emptyList()), CustomPrices.parse(null))
    }
}
