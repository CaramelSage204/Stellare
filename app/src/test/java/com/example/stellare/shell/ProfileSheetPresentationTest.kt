package com.example.stellare.shell

import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.shell.ProfileSheetPresentation
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSheetPresentationTest {

    @Test
    fun `inicjal to pierwsza litera imienia wielka`() {
        assertEquals("A", ProfileSheetPresentation.avatarInitial("anna"))
    }

    @Test
    fun `brak imienia daje znak zapytania`() {
        assertEquals("?", ProfileSheetPresentation.avatarInitial(null))
        assertEquals("?", ProfileSheetPresentation.avatarInitial(""))
    }

    @Test
    fun `nazwa wyswietlana sklada imie i nazwisko`() {
        assertEquals("Anna Nowak", ProfileSheetPresentation.displayName("Anna", "Nowak"))
    }

    @Test
    fun `nazwa wyswietlana bez nazwiska nie ma zbednej spacji`() {
        assertEquals("Anna", ProfileSheetPresentation.displayName("Anna", ""))
        assertEquals("Nowak", ProfileSheetPresentation.displayName(null, "Nowak"))
    }

    @Test
    fun `nazwa wyswietlana bez danych to Uzytkownik`() {
        assertEquals("Użytkownik", ProfileSheetPresentation.displayName(null, null))
        assertEquals("Użytkownik", ProfileSheetPresentation.displayName("", ""))
    }

    @Test
    fun `etykieta roli odpowiada kazdej roli`() {
        assertEquals("Psycholog", ProfileSheetPresentation.roleLabel(UserRole.PSYCHOLOGIST))
        assertEquals("Student Psychologii", ProfileSheetPresentation.roleLabel(UserRole.PSYCHOLOGY_STUDENT))
        assertEquals("Pacjent", ProfileSheetPresentation.roleLabel(UserRole.PATIENT))
    }

    @Test
    fun `brak roli jest pokazany jako pacjent`() {
        assertEquals("Pacjent", ProfileSheetPresentation.roleLabel(null))
    }

    @Test
    fun `naglowek ulubionych zalezy od roli ogladajacego`() {
        assertEquals("Ulubieni pacjenci", ProfileSheetPresentation.favoritesHeading(true))
        assertEquals("Ulubieni psycholodzy", ProfileSheetPresentation.favoritesHeading(false))
    }

    @Test
    fun `psycholog widzi przy ulubionym pacjencie wiek`() {
        assertEquals("Pacjent • 34 lat", ProfileSheetPresentation.favoriteSubtitle(true, 34, "ignorowane"))
    }

    @Test
    fun `pacjent widzi przy ulubionym psychologu pierwsza specjalizacje`() {
        assertEquals(
            "Terapia par",
            ProfileSheetPresentation.favoriteSubtitle(false, 0, " Terapia par , Uzależnienia")
        )
    }

    @Test
    fun `brak specjalizacji daje etykiete Psycholog`() {
        assertEquals("Psycholog", ProfileSheetPresentation.favoriteSubtitle(false, 0, ""))
        assertEquals("Psycholog", ProfileSheetPresentation.favoriteSubtitle(false, 0, " , Uzależnienia"))
    }
}
