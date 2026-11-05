package com.example.stellare.wallet

import com.example.stellare.ui.wallet.CoinPack
import com.example.stellare.ui.wallet.TopUpNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopUpNoticeTest {

    private val futureMarkers = listOf(
        "kolejn", "dojd", "wkrótce", "wkrotce", "przyszł", "przyszl", "później", "pozniej",
        "niebawem", "planuj", "będzie", "bedzie", "wersjach", "wersji"
    )

    @Test
    fun `zdanie o braku platnosci mowi tylko o stanie obecnym`() {
        val notice = TopUpNotice.NO_PAYMENT.lowercase()
        futureMarkers.forEach { marker ->
            assertFalse(
                "zdanie o braku płatności zapowiada przyszłość (zawiera \"$marker\"): ${TopUpNotice.NO_PAYMENT}",
                notice.contains(marker)
            )
        }
    }

    @Test
    fun `zdanie o braku platnosci mowi wprost ze nic nie jest pobierane`() {
        assertTrue(
            "zdanie musi mówić, że płatność nie jest pobierana: ${TopUpNotice.NO_PAYMENT}",
            TopUpNotice.NO_PAYMENT.contains("bez pobierania płatności")
        )
        assertTrue(
            "zdanie musi być o tej wersji: ${TopUpNotice.NO_PAYMENT}",
            TopUpNotice.NO_PAYMENT.startsWith("Ta wersja")
        )
    }

    @Test
    fun `cena pakietu jest opisana jako wartosc a nie kwota do zaplaty`() {
        val pack = CoinPack("Pakiet Mały", 50, "50,00 PLN", "opis")
        val description = TopUpNotice.packValueDescription(pack)
        assertEquals("wartość 50,00 PLN", description)
        listOf("zapłać", "zaplac", "do zapłaty", "płatność", "cena").forEach { word ->
            assertFalse(
                "opis ceny pakietu sugeruje płatność (zawiera \"$word\"): $description",
                description.lowercase().contains(word)
            )
        }
    }
}
