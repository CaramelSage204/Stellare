package com.example.stellare.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSheetLayoutTest {

    @Test
    fun `w poziomie tresc arkusza jest ograniczona do ekranu zeby dalo sie ja przewijac`() {
        val landscape = ProfileSheetLayout.contentMaxHeightDp(screenHeightDp = 360f, verticalInsetsDp = 24f)
        assertEquals(
            "w poziomie (360 dp wysokości) treść ma mieć wysokość ekranu bez pasków i uchwytu, a nie rosnąć bez granic",
            288f,
            landscape,
            0.01f
        )
    }

    @Test
    fun `w pionie ograniczenie nie przycina arkusza z kilkunastoma pozycjami`() {
        val portrait = ProfileSheetLayout.contentMaxHeightDp(screenHeightDp = 800f, verticalInsetsDp = 72f)
        assertTrue("w pionie treść ma mieć miejsce na wszystkie pozycje, jest $portrait", portrait >= 600f)
    }

    @Test
    fun `bardzo niski ekran nie daje zerowej wysokosci`() {
        val tiny = ProfileSheetLayout.contentMaxHeightDp(screenHeightDp = 100f, verticalInsetsDp = 60f)
        assertEquals("treść arkusza ma zawsze minimalną wysokość", ProfileSheetLayout.MIN_CONTENT_DP, tiny, 0.01f)
    }
}
