package com.example.stellare.posts

import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakePostDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.posts.PostViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModelDla(user: UserModel?): Pair<PostViewModel, FakePostDataSource> {
        val postDataSource = FakePostDataSource()
        val userDataSource = FakeUserDataSource(if (user != null) listOf(user) else emptyList())
        val userRepository = UserRepository(FakeAuthDataSource(user?.id), userDataSource, FakeFavoriteDataSource())
        val vm = PostViewModel(PostRepository(postDataSource), userRepository)
        return vm to postDataSource
    }

    @Test
    fun `psycholog publikuje oglosznie oferujace z cena wynikajaca z profilu`() = runTest(dispatcher) {
        val psycholog = UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST, pricePerSession = 150.0)
        val (vm, postDataSource) = viewModelDla(psycholog)

        vm.publish("Konsultacje", "Opis oferty", listOf("lek"))

        val zapisany = postDataSource.posts.value.single()
        assertEquals(PostType.OFFERING_THERAPY, zapisany.type)
        assertEquals(150, zapisany.priceCoins)
    }

    @Test
    fun `student psychologii publikuje oglosznie oferujace z cena wynikajaca z profilu`() = runTest(dispatcher) {
        val student = UserModel(id = "student1", role = UserRole.PSYCHOLOGY_STUDENT, pricePerSession = 80.0)
        val (vm, postDataSource) = viewModelDla(student)

        vm.publish("Konsultacje", "Opis oferty", listOf("lek"))

        val zapisany = postDataSource.posts.value.single()
        assertEquals(PostType.OFFERING_THERAPY, zapisany.type)
        assertEquals(80, zapisany.priceCoins)
    }

    @Test
    fun `pacjent publikuje ogloszenie poszukujace z zerowa cena`() = runTest(dispatcher) {
        val pacjent = UserModel(id = "pat1", role = UserRole.PATIENT)
        val (vm, postDataSource) = viewModelDla(pacjent)

        vm.publish("Szukam pomocy", "Opis potrzeby", emptyList())

        val zapisany = postDataSource.posts.value.single()
        assertEquals(PostType.SEEKING_PSYCHOLOGIST, zapisany.type)
        assertEquals(0, zapisany.priceCoins)
    }

    @Test
    fun `authorSnapshot isVerified true dla zweryfikowanego autora`() = runTest(dispatcher) {
        val zweryfikowany = UserModel(id = "u1", role = UserRole.PATIENT, isVerified = true)
        val (vm, postDataSource) = viewModelDla(zweryfikowany)

        vm.publish("Tytul", "Tresc", emptyList())

        assertTrue(postDataSource.posts.value.single().authorSnapshot.isVerified)
    }

    @Test
    fun `authorSnapshot isVerified false dla niezweryfikowanego autora`() = runTest(dispatcher) {
        val niezweryfikowany = UserModel(id = "u2", role = UserRole.PATIENT, isVerified = false)
        val (vm, postDataSource) = viewModelDla(niezweryfikowany)

        vm.publish("Tytul", "Tresc", emptyList())

        assertFalse(postDataSource.posts.value.single().authorSnapshot.isVerified)
    }

    @Test
    fun `publish bez zalogowanego uzytkownika nie zapisuje niczego`() = runTest(dispatcher) {
        val (vm, postDataSource) = viewModelDla(null)

        vm.publish("Tytul", "Tresc", emptyList())

        assertTrue(postDataSource.posts.value.isEmpty())
    }

    @Test
    fun `deactivate ustawia isActive na false i nie usuwa dokumentu`() = runTest(dispatcher) {
        val autor = UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST, pricePerSession = 100.0)
        val (vm, postDataSource) = viewModelDla(autor)
        postDataSource.posts.value = listOf(
            PostModel(id = "p1", authorId = autor.id, isActive = true)
        )

        vm.deactivate("p1")
        advanceUntilIdle()

        val posty = postDataSource.posts.value
        assertEquals(1, posty.size)
        assertFalse(posty.single().isActive)
    }

    @Test
    fun `pusty tytul zglasza blad i nic nie zapisuje`() = runTest(dispatcher) {
        val (vm, postDataSource) = viewModelDla(UserModel(id = "u1", role = UserRole.PATIENT))

        vm.publish("", "Tresc", emptyList())

        assertEquals("Tytuł nie może być pusty.", vm.publishError.value)
        assertTrue(postDataSource.posts.value.isEmpty())
    }

    @Test
    fun `tytul powyzej 120 znakow zglasza blad`() = runTest(dispatcher) {
        val (vm, postDataSource) = viewModelDla(UserModel(id = "u1", role = UserRole.PATIENT))

        vm.publish("a".repeat(121), "Tresc", emptyList())

        assertEquals("Tytuł może mieć najwyżej 120 znaków.", vm.publishError.value)
        assertTrue(postDataSource.posts.value.isEmpty())
    }

    @Test
    fun `tresc powyzej 2000 znakow zglasza blad`() = runTest(dispatcher) {
        val (vm, postDataSource) = viewModelDla(UserModel(id = "u1", role = UserRole.PATIENT))

        vm.publish("Tytul", "a".repeat(2001), emptyList())

        assertEquals("Treść może mieć najwyżej 2000 znaków.", vm.publishError.value)
        assertTrue(postDataSource.posts.value.isEmpty())
    }

    @Test
    fun `wiecej niz 5 tagow zglasza blad`() = runTest(dispatcher) {
        val (vm, postDataSource) = viewModelDla(UserModel(id = "u1", role = UserRole.PATIENT))

        vm.publish("Tytul", "Tresc", listOf("1", "2", "3", "4", "5", "6"))

        assertEquals("Można wybrać najwyżej 5 tagów.", vm.publishError.value)
        assertTrue(postDataSource.posts.value.isEmpty())
    }

    @Test
    fun `clearPublishError czysci blad`() = runTest(dispatcher) {
        val (vm, _) = viewModelDla(UserModel(id = "u1", role = UserRole.PATIENT))

        vm.publish("", "Tresc", emptyList())
        assertEquals("Tytuł nie może być pusty.", vm.publishError.value)

        vm.clearPublishError()

        assertNull(vm.publishError.value)
    }

    @Test
    fun `udana publikacja zwraca true`() = runTest(dispatcher) {
        val (vm, _) = viewModelDla(UserModel(id = "u1", role = UserRole.PATIENT))

        val wynik = vm.publish("Tytul", "Tresc", emptyList())

        assertTrue(wynik)
    }

    @Test
    fun `publikacja odrzucona przez walidacje zwraca false i nic nie zapisuje`() = runTest(dispatcher) {
        val (vm, postDataSource) = viewModelDla(UserModel(id = "u1", role = UserRole.PATIENT))

        val wynik = vm.publish("", "Tresc", emptyList())

        assertFalse(wynik)
        assertTrue(postDataSource.posts.value.isEmpty())
    }

    @Test
    fun `seekingPosts i offeringPosts filtruja po typie ogloszenia`() = runTest(dispatcher) {
        val (vm, postDataSource) = viewModelDla(UserModel(id = "u1", role = UserRole.PSYCHOLOGIST))
        backgroundScope.launch { vm.seekingPosts.collect {} }
        backgroundScope.launch { vm.offeringPosts.collect {} }
        postDataSource.posts.value = listOf(
            PostModel(id = "s1", type = PostType.SEEKING_PSYCHOLOGIST, isActive = true),
            PostModel(id = "o1", type = PostType.OFFERING_THERAPY, isActive = true)
        )
        advanceUntilIdle()

        assertEquals(listOf("s1"), vm.seekingPosts.value.map { it.id })
        assertEquals(listOf("o1"), vm.offeringPosts.value.map { it.id })
    }
}
