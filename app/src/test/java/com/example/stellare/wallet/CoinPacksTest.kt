package com.example.stellare.wallet

import com.example.stellare.ui.wallet.CoinPacks
import com.example.stellare.ui.wallet.TopUpLimits
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoinPacksTest {

    @Test
    fun `lista pakietow nie jest pusta`() {
        assertTrue("brak pakietów do doładowania", CoinPacks.isNotEmpty())
    }

    @Test
    fun `cena pakietu odpowiada liczbie monet jeden do jednego`() {
        CoinPacks.forEach { pack ->
            assertEquals(
                "pakiet \"${pack.name}\" daje ${pack.coins} monet, a pokazuje cenę ${pack.price}",
                "${pack.coins},00 PLN",
                pack.price
            )
        }
    }

    @Test
    fun `kazdy pakiet miesci sie w limicie jednorazowego doladowania`() {
        CoinPacks.forEach { pack ->
            assertTrue(
                "aplikacja oferuje pakiet \"${pack.name}\" (${pack.coins} monet), którego nie przyjmie: dozwolone ${TopUpLimits.RANGE}",
                pack.coins in TopUpLimits.RANGE
            )
        }
    }

    @Test
    fun `nazwy pakietow sa unikalne`() {
        val names = CoinPacks.map { it.name }
        assertEquals("zduplikowane nazwy pakietów: $names", names.distinct(), names)
    }

    @Test
    fun `limit doladowania w regulach zgadza sie z limitem w aplikacji`() {
        val reguly = File("../firebase/firestore.rules").readText()
        val limit = Regex("""coinsBalance\s*-\s*resource\.data\.coinsBalance\s*<=\s*(\d+)""")
            .find(reguly)?.groupValues?.get(1)?.toInt()
        assertEquals(
            "reguły przyjmą doładowanie o $limit monet, a aplikacja pozwala na ${TopUpLimits.MAX_COINS}: aplikacja oferuje to, czego reguły nie przyjmą (lub odwrotnie)",
            TopUpLimits.MAX_COINS,
            limit
        )
    }
}
