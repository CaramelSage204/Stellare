package com.example.stellare.resilience

import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.remote.UserDocumentWatch
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migawka z `exists() == false` znaczy dwie różne rzeczy. Tylko jedna z nich wolno zamienić na
 * zaproszenie do założenia profilu; druga, zamieniona na to samo, kasuje prawdziwy profil.
 */
class UserDocumentWatchTest {

    private val psycholog = UserModel(
        id = "p1", firstName = "Jan", lastName = "Lis", role = UserRole.PSYCHOLOGIST, pricePerSession = 180.0
    )

    @Test
    fun `nieobecnosc z samej pamieci podrecznej nie jest brakiem profilu`() {
        val odczyt = UserDocumentWatch().read(user = null, exists = false, fromCache = true, error = null)

        assertTrue(
            "pierwszy start bez sieci i z czysta pamiecia podreczna NIE jest dowodem, ze profilu nie ma. " +
                "Zwrocenie tu CurrentUserRead.Absent sadza zalogowanego psychologa na ekranie " +
                "\"Uzupelnij profil\" i zaprasza go do zalozenia nowego, ktory po powrocie sieci " +
                "NADPISZE jego prawdziwy profil. Odczyt: $odczyt",
            odczyt is CurrentUserRead.Unavailable
        )
    }

    @Test
    fun `nieobecnosc potwierdzona przez serwer jest brakiem profilu`() {
        val odczyt = UserDocumentWatch().read(user = null, exists = false, fromCache = false, error = null)

        assertEquals(
            "serwer odpowiedzial, ze dokumentu nie ma — bez tego nowe konto z logowania Google " +
                "nigdy nie trafi na zakladanie profilu i zostanie zamkniete poza aplikacja",
            CurrentUserRead.Absent,
            odczyt
        )
    }

    @Test
    fun `raz potwierdzona nieobecnosc zostaje nieobecnoscia po utracie sieci`() {
        val watch = UserDocumentWatch()
        watch.read(user = null, exists = false, fromCache = false, error = null)

        val poUtracieSieci = watch.read(user = null, exists = false, fromCache = true, error = null)

        assertEquals(
            "utrata sieci w trakcie wypelniania formularza zakladania profilu nie moze przestawic " +
                "stanu na \"nie wiem\": bramka nawigacji wyrzucilaby wtedy uzytkownika z formularza " +
                "i skasowala wpisane pola",
            CurrentUserRead.Absent,
            poUtracieSieci
        )
    }

    @Test
    fun `profil z pamieci podrecznej jest wczytanym profilem`() {
        val odczyt = UserDocumentWatch().read(user = psycholog, exists = true, fromCache = true, error = null)

        assertEquals(
            "praca bez sieci na zapisanym profilu musi dalej dzialac; inaczej kazde wyjscie z zasiegu " +
                "gasi aplikacje zalogowanemu uzytkownikowi",
            CurrentUserRead.Loaded(psycholog),
            odczyt
        )
    }

    @Test
    fun `odmowa dostepu nie jest brakiem profilu nawet gdy przyszla z danymi`() {
        val odmowa = FirebaseFirestoreException("permission denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

        val odczyt = UserDocumentWatch().read(user = psycholog, exists = true, fromCache = false, error = odmowa)

        assertTrue(
            "uniewaznienie tokenu konczy sie odmowa nasluchu; wynik musi byc awaria odczytu, " +
                "a nie cichym podaniem danych jako aktualnych. Odczyt: $odczyt",
            odczyt is CurrentUserRead.Unavailable
        )
        assertEquals(odmowa, (odczyt as CurrentUserRead.Unavailable).error)
    }

    @Test
    fun `istniejacy dokument bez odwzorowania nie jest brakiem profilu`() {
        val watch = UserDocumentWatch()
        watch.read(user = psycholog, exists = true, fromCache = false, error = null)

        val odczyt = watch.read(user = null, exists = true, fromCache = false, error = null)

        assertTrue(
            "o tym profilu wiadomo, ze istnieje — zaproponowanie zalozenia nowego nadpisaloby go. " +
                "Odczyt: $odczyt",
            odczyt is CurrentUserRead.Unavailable
        )
    }

    @Test
    fun `nasluch zalozony od nowa nie dziedziczy potwierdzenia z poprzedniego`() {
        val pierwszy = UserDocumentWatch()
        pierwszy.read(user = null, exists = false, fromCache = false, error = null)

        val drugi = UserDocumentWatch()
        val odczyt = drugi.read(user = null, exists = false, fromCache = true, error = null)

        assertTrue(
            "potwierdzenie serwera nalezy do jednego nasluchu; nowy nasluch bez sieci znow nic nie wie " +
                "i nie wolno mu zglosic braku profilu. Odczyt: $odczyt",
            odczyt is CurrentUserRead.Unavailable
        )
    }
}
