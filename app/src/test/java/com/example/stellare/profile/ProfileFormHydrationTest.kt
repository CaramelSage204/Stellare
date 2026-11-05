package com.example.stellare.profile

import com.example.stellare.ui.profile.shouldHydrateProfileForm
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFormHydrationTest {

    @Test
    fun `pierwsze wczytanie profilu wypelnia formularz`() {
        assertTrue(
            "przy pierwszym wczytaniu użytkownika formularz ma dostać dane z profilu",
            shouldHydrateProfileForm(hydratedUserId = null, loadedUserId = "u1")
        )
    }

    @Test
    fun `po odtworzeniu aktywnosci dane z profilu nie nadpisuja tego co uzytkownik wpisal`() {
        assertFalse(
            "formularz wypełniony dla u1 i odtworzony po obrocie nie może być nadpisany danymi z profilu (wyczyszczona cena wracałaby do 80)",
            shouldHydrateProfileForm(hydratedUserId = "u1", loadedUserId = "u1")
        )
    }

    @Test
    fun `inny uzytkownik wypelnia formularz od nowa`() {
        assertTrue(
            "po zmianie zalogowanego użytkownika formularz ma dostać dane nowego profilu",
            shouldHydrateProfileForm(hydratedUserId = "u1", loadedUserId = "u2")
        )
    }
}
