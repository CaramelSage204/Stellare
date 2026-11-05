package com.example.stellare.data

import com.example.stellare.data.model.PrivateProfile
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.remote.UserProfileFields
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.util.CustomClassMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDocumentSerializationTest {

    @Suppress("UNCHECKED_CAST")
    private fun serialized(user: Any): Map<String, Any?> =
        CustomClassMapper.convertToPlainJavaTypes(user) as Map<String, Any?>

    @Test
    fun `dokument uzytkownika wysylany przy rejestracji nie niesie pol oceny`() {
        val pola = serialized(UserModel(id = "nowy", firstName = "Jan")).keys
        assertFalse(
            "rejestracja wyśle pola oceny i dostanie PERMISSION_DENIED: konto powstanie bez profilu",
            pola.any { it == "ratingSum" || it == "ratingCount" }
        )
    }

    private val fullUser = UserModel(
        id = "u", firstName = "Jan", lastName = "Nowak", age = 41, gender = "M", role = UserRole.PSYCHOLOGIST,
        isVerified = true, isAdmin = false, qualifications = "mgr", specializations = "CBT", pricePerSession = 120.5,
        bio = "bio", customPrices = "30:50", profileImageUri = "uri"
    )

    @Test
    fun `zestaw pol dokumentu uzytkownika jest dokladnie tym, ktory przyjmuja reguly przy tworzeniu profilu`() {
        assertEquals(
            "dokument profilu rozjechał się z zestawem pól w firebase/test/users.test.js (test rejestracji)",
            setOf(
                "id", "role", "isVerified", "isAdmin", "firstName", "lastName", "age",
                "gender", "qualifications", "specializations", "pricePerSession",
                "bio", "customPrices", "profileImageUri"
            ),
            serialized(UserModel(id = "nowy")).keys
        )
    }

    @Test
    fun `model uzytkownika nie niesie zadnego pola prywatnego, wiec zaden zapis profilu nie wyniesie telefonu, e-maila ani salda`() {
        val leaked = serialized(fullUser).keys.intersect(UserProfileFields.LEGACY_PRIVATE_NAMES.toSet())
        assertTrue(
            "pole prywatne wróciło do modelu publicznego: $leaked. Zapis profilu wysłałby je do dokumentu czytanego przez każdego, " +
                "a saldo przestałoby być chronione wyłącznie maską pól",
            leaked.isEmpty()
        )
    }

    @Test
    fun `lista pol profilu jest dokladnie zestawem pol modelu i nie zawiera zadnego pola prywatnego`() {
        assertEquals(
            "pole profilu poza listą zapisu nie zostanie zapisane, a pole spoza modelu rozjedzie zapis",
            serialized(fullUser).keys,
            UserProfileFields.NAMES.toSet()
        )
        assertTrue(
            "lista pól wysyłanych przez klienta do profilu publicznego zawiera pole prywatne",
            UserProfileFields.NAMES.intersect(UserProfileFields.LEGACY_PRIVATE_NAMES.toSet()).isEmpty()
        )
    }

    @Test
    fun `zapis profilu niesie wartosci dokladnie takie, jakie wysylalaby serializacja modelu`() {
        assertEquals(
            "ręcznie zbudowana mapa zapisu rozjechała się z serializacją modelu: pole trafi do bazy z inną wartością",
            serialized(fullUser),
            UserProfileFields.profileFields(fullUser)
        )
        assertEquals(serialized(UserModel(id = "nowy")), UserProfileFields.profileFields(UserModel(id = "nowy")))
    }

    @Test
    fun `zapis profilu czysci pola prywatne starego profilu razem z reszta zmiany, a nie niesie ich wartosci`() {
        val write = UserProfileFields.profileWrite(fullUser)
        UserProfileFields.LEGACY_PRIVATE_NAMES.forEach { name ->
            assertTrue(
                "zapis profilu nie usuwa pola $name: reguły odrzucą każdy zapis starego profilu, który je jeszcze niesie",
                write[name] == FieldValue.delete()
            )
        }
        assertEquals(
            "poza czyszczeniem zapis niesie dokładnie pola profilu",
            UserProfileFields.profileFields(fullUser),
            write.filterKeys { it !in UserProfileFields.LEGACY_PRIVATE_NAMES }
        )
    }

    @Test
    fun `zatwierdzenie weryfikacji ustawia odznake i czysci pola prywatne w tym samym zapisie`() {
        val approval = UserProfileFields.verificationApproval()
        assertEquals(true, approval["isVerified"])
        UserProfileFields.LEGACY_PRIVATE_NAMES.forEach { name ->
            assertTrue(
                "zatwierdzenie nie usuwa pola $name: administrator dostanie odmowę przy zatwierdzaniu starego profilu",
                approval[name] == FieldValue.delete()
            )
        }
        assertEquals(setOf("isVerified") + UserProfileFields.LEGACY_PRIVATE_NAMES, approval.keys)
    }

    @Test
    fun `dokument prywatny zakladany przy rejestracji niesie jawnie phone, email i coinsBalance rowne zero`() {
        val document = serialized(PrivateProfile(phone = "", email = "jan@example.com", coinsBalance = 0))
        assertEquals(
            "reguła tworzenia wymaga dokładnie tych pól; dokument bez coinsBalance zostawia konto na stałe bez portfela",
            setOf("phone", "email", "coinsBalance"),
            document.keys
        )
        assertEquals("saldo startowe musi być obecne i równe 0", 0, document["coinsBalance"])
    }
}
