package com.example.stellare.profile

import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.profile.ServiceKind
import com.example.stellare.ui.profile.isSpecialist
import com.example.stellare.ui.profile.newServicePrice
import com.example.stellare.ui.profile.profileSubtitle
import com.example.stellare.ui.profile.serviceKind
import com.example.stellare.ui.profile.sessionPriceText
import com.example.stellare.ui.profile.priceToSave
import com.example.stellare.ui.profile.priceError
import com.example.stellare.ui.profile.newServiceError
import com.example.stellare.ui.profile.canSave
import com.example.stellare.ui.session.RegistrationRules
import org.junit.Assert.assertNull
import com.example.stellare.ui.profile.specializationSet
import com.example.stellare.ui.profile.specializationsText
import com.example.stellare.ui.profile.toggled
import com.example.stellare.ui.profile.withoutIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEditPresentationTest {

    @Test
    fun `usluga z wideo w nazwie dostaje ikone wideo`() {
        assertEquals(ServiceKind.Video, serviceKind("Sesja wideo"))
        assertEquals(ServiceKind.Video, serviceKind("SESJA WIDEO"))
    }

    @Test
    fun `kazda inna usluga dostaje ikone audio`() {
        assertEquals(ServiceKind.Audio, serviceKind("Sesja audio"))
        assertEquals(ServiceKind.Audio, serviceKind("Konsultacja"))
    }

    @Test
    fun `nowa stawka z poprawnej liczby calkowitej`() {
        assertEquals(250, newServicePrice("250"))
        assertEquals(250, newServicePrice(" 250 "))
    }

    @Test
    fun `pusta nowa stawka nie zamienia sie w liczbe`() {
        assertNull(newServicePrice(""))
        assertNull(newServicePrice("   "))
    }

    @Test
    fun `nieczytelna zerowa lub ulamkowa nowa stawka jest odrzucona`() {
        assertNull(newServicePrice("abc"))
        assertNull(newServicePrice("0"))
        assertNull(newServicePrice("-5"))
        assertNull(newServicePrice("12,5"))
        assertNull(newServicePrice("12.5"))
    }

    @Test
    fun `komunikat nowej stawki mowi co jest nie tak`() {
        assertEquals(RegistrationRules.PRICE_REQUIRED, newServiceError(""))
        assertEquals(RegistrationRules.PRICE_INVALID, newServiceError("0"))
        assertNull(newServiceError("100"))
    }

    @Test
    fun `cena calkowita pokazana bez ulamka`() {
        assertEquals("80", sessionPriceText(80.0))
    }

    @Test
    fun `cena ulamkowa z bazy pokazana w calosci`() {
        assertEquals("80.5", sessionPriceText(80.5))
    }

    @Test
    fun `cena domyslna specjalisty z poprawnej liczby calkowitej`() {
        assertEquals(80.0, priceToSave(true, "80", 55.0)!!, 0.0)
    }

    @Test
    fun `pusta cena domyslna specjalisty blokuje zapis`() {
        assertNull(priceToSave(true, "", 55.0))
        assertEquals(RegistrationRules.PRICE_REQUIRED, priceError(true, ""))
    }

    @Test
    fun `zerowa nieczytelna i ulamkowa cena domyslna blokuje zapis`() {
        listOf("0", "abc", "80.5", "-3").forEach { text ->
            assertNull(text, priceToSave(true, text, 55.0))
            assertEquals(text, RegistrationRules.PRICE_INVALID, priceError(true, text))
        }
    }

    @Test
    fun `pacjent zachowuje cene z profilu i nie ma bledu ceny`() {
        assertEquals(0.0, priceToSave(false, "", 0.0)!!, 0.0)
        assertNull(priceError(false, ""))
    }

    @Test
    fun `podtytul sklada role i wiek`() {
        assertEquals("Psycholog • 41 lat", profileSubtitle(UserRole.PSYCHOLOGIST, 41))
        assertEquals("Student Psychologii • 24 lat", profileSubtitle(UserRole.PSYCHOLOGY_STUDENT, 24))
        assertEquals("Pacjent • 30 lat", profileSubtitle(UserRole.PATIENT, 30))
    }

    @Test
    fun `brak uzytkownika to brak podtytulu`() {
        assertNull(profileSubtitle(null, null))
        assertNull(profileSubtitle(null, 30))
    }

    @Test
    fun `wiek zero lub ujemny nie jest pokazywany`() {
        assertEquals("Pacjent", profileSubtitle(UserRole.PATIENT, 0))
        assertEquals("Psycholog", profileSubtitle(UserRole.PSYCHOLOGIST, -1))
    }

    @Test
    fun `zapis wymaga zaladowanego uzytkownika i poprawnej ceny`() {
        assertTrue(canSave(true, null))
        assertFalse(canSave(false, null))
        assertFalse(canSave(true, RegistrationRules.PRICE_REQUIRED))
    }

    @Test
    fun `sekcje specjalisty widza psycholog i student`() {
        assertTrue(isSpecialist(UserRole.PSYCHOLOGIST))
        assertTrue(isSpecialist(UserRole.PSYCHOLOGY_STUDENT))
    }

    @Test
    fun `sekcji specjalisty nie widzi pacjent ani brak uzytkownika`() {
        assertFalse(isSpecialist(UserRole.PATIENT))
        assertFalse(isSpecialist(null))
    }

    @Test
    fun `specjalizacje z tekstu z obcieciem spacji i pustych`() {
        assertEquals(setOf("A", "B C"), specializationSet(" A , B C ,, "))
        assertEquals(emptySet<String>(), specializationSet(""))
    }

    @Test
    fun `specjalizacje do tekstu przecinek i spacja`() {
        assertEquals("A, B", specializationsText(linkedSetOf("A", "B")))
    }

    @Test
    fun `przelaczenie dodaje brakujaca specjalizacje`() {
        assertEquals(setOf("A", "B"), setOf("A").toggled("B"))
    }

    @Test
    fun `przelaczenie usuwa zaznaczona specjalizacje`() {
        assertEquals(setOf("A"), setOf("A", "B").toggled("B"))
    }

    @Test
    fun `usuniecie stawki po indeksie zostawia pozostale w kolejnosci`() {
        val list = listOf("a" to 1, "b" to 2, "c" to 3)
        assertEquals(listOf("a" to 1, "c" to 3), list.withoutIndex(1))
    }

    @Test
    fun `usuniecie poza zakresem nic nie zmienia`() {
        val list = listOf("a" to 1)
        assertEquals(list, list.withoutIndex(5))
    }
}
