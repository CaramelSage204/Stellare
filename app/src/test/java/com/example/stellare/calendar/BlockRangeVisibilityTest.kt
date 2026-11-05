package com.example.stellare.calendar

import com.example.stellare.ui.calendar.BlockRangeVisibility
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockRangeVisibilityTest {

    private val minHeight = 44f

    @Test
    fun `przy normalnej czcionce blok o minimalnej wysokosci pokazuje zakres godzin`() {
        assertTrue("blok dokladnie 44 dp ma pokazac zakres", BlockRangeVisibility.showsRange(44f, minHeight, 1f))
    }

    @Test
    fun `przy normalnej czcionce blok nizszy od minimum chowa zakres`() {
        assertFalse("blok 43,9 dp nie ma miejsca na zakres", BlockRangeVisibility.showsRange(43.9f, minHeight, 1f))
    }

    @Test
    fun `wieksza czcionka podnosi prog wysokosci proporcjonalnie`() {
        assertFalse("przy skali 1,5 blok 62 dp jest za niski, bo prog to 66 dp", BlockRangeVisibility.showsRange(62f, minHeight, 1.5f))
        assertTrue("przy skali 1,5 blok 66 dp wystarcza", BlockRangeVisibility.showsRange(66f, minHeight, 1.5f))
    }

    @Test
    fun `mniejsza czcionka niz systemowa nie obniza progu`() {
        assertFalse("przy skali 0,85 blok 40 dp nadal jest za niski", BlockRangeVisibility.showsRange(40f, minHeight, 0.85f))
    }
}
