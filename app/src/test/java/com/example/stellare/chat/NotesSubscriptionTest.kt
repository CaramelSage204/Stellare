package com.example.stellare.chat

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.data.model.ChatModel
import com.example.stellare.data.model.NoteModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.ChatRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeChatDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeMessageDataSource
import com.example.stellare.fakes.FakeNoteDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesSubscriptionTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val czat = ChatModel(
        chatId = "c1",
        psychologistId = "psy",
        patientId = "pac",
        participants = listOf("psy", "pac")
    )

    private class Uklad(val vm: ChatViewModel, val notes: FakeNoteDataSource)

    private fun zbuduj(signedInAs: String?): Uklad {
        val users = FakeUserDataSource(
            listOf(UserModel(id = "psy", role = UserRole.PSYCHOLOGIST), UserModel(id = "pac", role = UserRole.PATIENT))
        )
        val chats = FakeChatDataSource().also { it.chats.value = listOf(czat) }
        val notes = FakeNoteDataSource().also {
            it.notes.value = listOf(NoteModel(noteId = "n1", chatId = "c1", authorId = "psy", title = "t", content = "c"))
        }
        val userRepository = UserRepository(FakeAuthDataSource(signedInAs), users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users))
        return Uklad(ChatViewModel(ChatRepository(chats, FakeMessageDataSource(), notes), userRepository), notes)
    }

    @Test
    fun `pacjent wchodzac do czatu nie tworzy subskrypcji notatek`() = runTest(dispatcher) {
        val uklad = zbuduj(signedInAs = "pac")
        backgroundScope.launch { uklad.vm.notesFor("c1").collect {} }
        runCurrent()

        assertEquals(0, uklad.notes.subscriptionCount)
    }

    @Test
    fun `obcy uzytkownik nie tworzy subskrypcji notatek`() = runTest(dispatcher) {
        val uklad = zbuduj(signedInAs = "ktos")
        backgroundScope.launch { uklad.vm.notesFor("c1").collect {} }
        runCurrent()

        assertEquals(0, uklad.notes.subscriptionCount)
    }

    @Test
    fun `niezalogowany nie tworzy subskrypcji notatek`() = runTest(dispatcher) {
        val uklad = zbuduj(signedInAs = null)
        backgroundScope.launch { uklad.vm.notesFor("c1").collect {} }
        runCurrent()

        assertEquals(0, uklad.notes.subscriptionCount)
    }

    @Test
    fun `psycholog prowadzacy czat tworzy jedna subskrypcje i dostaje notatki`() = runTest(dispatcher) {
        val uklad = zbuduj(signedInAs = "psy")
        var ostatnie = emptyList<NoteModel>()
        backgroundScope.launch { uklad.vm.notesFor("c1").collect { ostatnie = it } }
        runCurrent()

        assertEquals(1, uklad.notes.subscriptionCount)
        assertEquals(listOf("n1"), ostatnie.map { it.noteId })
    }
}
