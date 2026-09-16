package com.example.stellare.fakes

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeUserDataSourceTest {

    private val psycholog = UserModel(id = "p1", firstName = "Ala", role = UserRole.PSYCHOLOGIST)
    private val pacjent = UserModel(id = "k1", firstName = "Bogdan", role = UserRole.PATIENT)

    @Test
    fun `zwraca uzytkownika po identyfikatorze`() = runTest {
        val source = FakeUserDataSource(listOf(psycholog, pacjent))
        assertEquals("Ala", source.getUser("p1").first()?.firstName)
    }

    @Test
    fun `zwraca null dla nieznanego identyfikatora`() = runTest {
        val source = FakeUserDataSource(listOf(psycholog))
        assertNull(source.getUser("nie-ma").first())
    }

    @Test
    fun `filtruje po roli`() = runTest {
        val source = FakeUserDataSource(listOf(psycholog, pacjent))
        assertEquals(listOf(pacjent), source.getAllByRole("PATIENT").first())
    }

    @Test
    fun `zapis podmienia istniejacego uzytkownika`() = runTest {
        val source = FakeUserDataSource(listOf(psycholog))
        source.saveUser(psycholog.copy(firstName = "Alicja"))
        assertEquals(1, source.users.value.size)
        assertEquals("Alicja", source.getUser("p1").first()?.firstName)
    }
}
