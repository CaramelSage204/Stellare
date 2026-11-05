package com.example.stellare.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PolishPluralTest {

    @Test
    fun `jedna opinia`() = assertEquals("1 opinia", PolishPlural.opinions(1))

    @Test
    fun `zero opinii`() = assertEquals("0 opinii", PolishPlural.opinions(0))

    @Test
    fun `od dwoch do czterech opinie`() {
        assertEquals("2 opinie", PolishPlural.opinions(2))
        assertEquals("3 opinie", PolishPlural.opinions(3))
        assertEquals("4 opinie", PolishPlural.opinions(4))
    }

    @Test
    fun `od piec opinii`() {
        assertEquals("5 opinii", PolishPlural.opinions(5))
        assertEquals("9 opinii", PolishPlural.opinions(9))
        assertEquals("10 opinii", PolishPlural.opinions(10))
    }

    @Test
    fun `nastki dwunastu do czternastu to opinii`() {
        assertEquals("11 opinii", PolishPlural.opinions(11))
        assertEquals("12 opinii", PolishPlural.opinions(12))
        assertEquals("13 opinii", PolishPlural.opinions(13))
        assertEquals("14 opinii", PolishPlural.opinions(14))
    }

    @Test
    fun `dwadziescia dwa do dwadziescia cztery to opinie`() {
        assertEquals("21 opinii", PolishPlural.opinions(21))
        assertEquals("22 opinie", PolishPlural.opinions(22))
        assertEquals("23 opinie", PolishPlural.opinions(23))
        assertEquals("24 opinie", PolishPlural.opinions(24))
        assertEquals("25 opinii", PolishPlural.opinions(25))
    }

    @Test
    fun `setki i tysiace`() {
        assertEquals("100 opinii", PolishPlural.opinions(100))
        assertEquals("101 opinii", PolishPlural.opinions(101))
        assertEquals("102 opinie", PolishPlural.opinions(102))
        assertEquals("112 opinii", PolishPlural.opinions(112))
        assertEquals("113 opinii", PolishPlural.opinions(113))
        assertEquals("122 opinie", PolishPlural.opinions(122))
        assertEquals("1002 opinie", PolishPlural.opinions(1002))
    }
}
