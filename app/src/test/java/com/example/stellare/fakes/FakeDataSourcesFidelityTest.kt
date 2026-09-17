package com.example.stellare.fakes

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.ChatModel
import com.example.stellare.data.model.FavoriteModel
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.model.VerificationRequestModel
import com.example.stellare.data.model.VerificationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeDataSourcesFidelityTest {

    @Test
    fun `dwukrotny zapis tego samego ulubionego daje jeden wpis`() = runTest {
        val source = FakeFavoriteDataSource()
        val fav = FavoriteModel(userId = "k1", psychologistId = "p1")
        source.saveFavorite(fav)
        source.saveFavorite(fav)
        assertEquals(1, source.favorites.value.size)
    }

    @Test
    fun `rezerwacja bez pokrycia rzuca wyjatek o braku monet`() = runTest {
        val users = FakeUserDataSource(
            listOf(
                UserModel(id = "patient1", role = UserRole.PATIENT, coinsBalance = 10),
                UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST, coinsBalance = 0)
            )
        )
        val appointments = FakeAppointmentDataSource(
            initial = listOf(
                AppointmentModel(appointmentId = "a1", psychologistId = "psych1", status = "FREE")
            ),
            users = users
        )

        val exception = try {
            appointments.bookAppointmentAtomically(
                appointmentId = "a1",
                patientId = "patient1",
                psychologistId = "psych1",
                priceCoins = 80,
                notes = ""
            )
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertNotNull(exception)
        assertEquals("Za mało monet w portfelu.", exception?.message)
    }

    @Test
    fun `aktualizacja nieistniejacego czatu rzuca wyjatek`() = runTest {
        val chats = FakeChatDataSource()

        val exception = try {
            chats.updateChat(ChatModel(chatId = "brak"))
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertNotNull(exception)
    }

    @Test
    fun `tryb zredukowany bez portfela przepuszcza rezerwacje mimo braku pokrycia`() = runTest {
        val termin = AppointmentModel(
            appointmentId = "t1", psychologistId = "p1", patientId = null,
            date = "2026-10-01", time = "10:00", status = "FREE", priceCoins = 80
        )
        val source = FakeAppointmentDataSource(listOf(termin))
        source.bookAppointmentAtomically("t1", "k1", "p1", 80, "")
        assertEquals("BOOKED", source.appointments.value.first().status)
    }

    @Test
    fun `rezerwacja przez nieznanego pacjenta traktuje saldo jak zero`() = runTest {
        val users = FakeUserDataSource(emptyList())
        val wallet = FakeWalletDataSource()
        val termin = AppointmentModel(
            appointmentId = "t1", psychologistId = "p1", patientId = null,
            date = "2026-10-01", time = "10:00", status = "FREE", priceCoins = 80
        )
        val source = FakeAppointmentDataSource(listOf(termin), users, wallet)
        val wyjatek = runCatching {
            source.bookAppointmentAtomically("t1", "nieznany", "p1", 80, "")
        }.exceptionOrNull()
        assertTrue(wyjatek is IllegalStateException)
    }

    @Test
    fun `getActivePosts pomija nieaktywne ogloszenia`() = runTest {
        val source = FakePostDataSource()
        source.savePost(
            PostModel(id = "p1", type = PostType.SEEKING_PSYCHOLOGIST, isActive = true)
        )
        source.savePost(
            PostModel(id = "p2", type = PostType.SEEKING_PSYCHOLOGIST, isActive = false)
        )

        val active = source.getActivePosts(PostType.SEEKING_PSYCHOLOGIST.name).first()

        assertEquals(1, active.size)
        assertEquals("p1", active.first().id)
    }

    @Test
    fun `deactivatePost ustawia isActive na false zamiast usuwac dokument`() = runTest {
        val source = FakePostDataSource()
        source.savePost(PostModel(id = "p1", isActive = true))

        source.deactivatePost("p1")

        val post = source.posts.value.firstOrNull { it.id == "p1" }
        assertNotNull(post)
        assertFalse(post!!.isActive)
    }

    @Test
    fun `savePost z pustym identyfikatorem rzuca wyjatek`() = runTest {
        val source = FakePostDataSource()

        val exception = try {
            source.savePost(PostModel(id = "", title = "Bez id"))
            null
        } catch (e: IllegalArgumentException) {
            e
        }

        assertNotNull(exception)
        assertEquals(0, source.posts.value.size)
    }

    @Test
    fun `decide zatwierdzone ustawia status APPROVED i odznake zweryfikowany`() = runTest {
        val users = FakeUserDataSource(listOf(UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST)))
        val source = FakeVerificationDataSource(users)
        source.submit(VerificationRequestModel(uid = "psych1", status = VerificationStatus.PENDING))

        source.decide(uid = "psych1", approved = true, note = "")

        val request = source.requests.value.first { it.uid == "psych1" }
        assertEquals(VerificationStatus.APPROVED, request.status)
        val user = users.users.value.first { it.id == "psych1" }
        assertTrue(user.isVerified)
    }

    @Test
    fun `decide odrzucone ustawia status i notatke, nie rusza odznaki`() = runTest {
        val users = FakeUserDataSource(listOf(UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST)))
        val source = FakeVerificationDataSource(users)
        source.submit(VerificationRequestModel(uid = "psych1", status = VerificationStatus.PENDING))

        source.decide(uid = "psych1", approved = false, note = "Nieczytelny skan")

        val request = source.requests.value.first { it.uid == "psych1" }
        assertEquals(VerificationStatus.REJECTED, request.status)
        assertEquals("Nieczytelny skan", request.reviewNote)
        val user = users.users.value.first { it.id == "psych1" }
        assertFalse(user.isVerified)
    }
}
