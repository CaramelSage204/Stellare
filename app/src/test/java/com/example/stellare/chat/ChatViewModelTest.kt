package com.example.stellare.chat

import com.example.stellare.data.model.ChatModel
import com.example.stellare.data.model.NoteModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.remote.ChatDataSource
import com.example.stellare.data.repository.ChatRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeChatDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeMessageDataSource
import com.example.stellare.fakes.FakeNoteDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.chat.ChatViewModel
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class ChatDataSourceZRzucajacymZapisem(
    private val delegate: FakeChatDataSource
) : ChatDataSource by delegate {
    override suspend fun saveChat(chat: ChatModel) {
        throw IllegalStateException("Zapis czatu nie powiodl sie")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun chatViewModelDlaPsychologa(
        psychId: String,
        chatSource: FakeChatDataSource = FakeChatDataSource(),
        messageSource: FakeMessageDataSource = FakeMessageDataSource(),
        noteSource: FakeNoteDataSource = FakeNoteDataSource()
    ): ChatViewModel {
        val userDataSource = FakeUserDataSource(listOf(UserModel(id = psychId, role = UserRole.PSYCHOLOGIST)))
        val userRepository = UserRepository(FakeAuthDataSource(psychId), userDataSource, FakeFavoriteDataSource())
        val chatRepository = ChatRepository(chatSource, messageSource, noteSource)
        return ChatViewModel(chatRepository, userRepository)
    }

    @Test
    fun `openChatWith zwraca id istniejacego czatu i nie tworzy nowego`() = runTest(dispatcher) {
        val chatSource = FakeChatDataSource()
        chatSource.chats.value = listOf(
            ChatModel(
                chatId = "istniejacy-1",
                psychologistId = "psych1",
                patientId = "pacjent1",
                participants = listOf("psych1", "pacjent1"),
                lastMessage = "Czesc",
                lastMessageTime = 100L
            )
        )
        val vm = chatViewModelDlaPsychologa("psych1", chatSource = chatSource)

        val chatId = vm.openChatWith("pacjent1")
        advanceUntilIdle()

        assertEquals("istniejacy-1", chatId)
        assertEquals(1, chatSource.chats.value.size)
    }

    @Test
    fun `openChatWith tworzy czat z dwoma uczestnikami gdy nie ma istniejacego`() = runTest(dispatcher) {
        val chatSource = FakeChatDataSource()
        val vm = chatViewModelDlaPsychologa("psych1", chatSource = chatSource)

        val chatId = vm.openChatWith("pacjent1")
        advanceUntilIdle()

        assertEquals("czat-1", chatId)
        val utworzony = chatSource.chats.value.single()
        assertEquals(2, utworzony.participants.size)
        assertEquals(listOf("psych1", "pacjent1"), utworzony.participants)
    }

    @Test
    fun `send zapisuje wiadomosc i aktualizuje ostatnia wiadomosc w czacie`() = runTest(dispatcher) {
        val chatSource = FakeChatDataSource()
        chatSource.chats.value = listOf(
            ChatModel(
                chatId = "czat-1",
                psychologistId = "psych1",
                patientId = "pacjent1",
                participants = listOf("psych1", "pacjent1"),
                lastMessage = "stara wiadomosc",
                lastMessageTime = 1L
            )
        )
        val messageSource = FakeMessageDataSource()
        val vm = chatViewModelDlaPsychologa("psych1", chatSource = chatSource, messageSource = messageSource)

        // Celowo BEZ subskrypcji vm.chats — sprawdzamy zachowanie bez aktywnego kolektora.
        vm.send("czat-1", "nowa wiadomosc")
        advanceUntilIdle()

        assertEquals(1, messageSource.messages.value.size)
        assertEquals("nowa wiadomosc", messageSource.messages.value.first().text)

        val zaktualizowanyCzat = chatSource.chats.value.first { it.chatId == "czat-1" }
        assertEquals("nowa wiadomosc", zaktualizowanyCzat.lastMessage)
    }

    @Test
    fun `openChatWith zwraca null gdy zapis nowego czatu rzuca wyjatek`() = runTest(dispatcher) {
        val chatSource = ChatDataSourceZRzucajacymZapisem(FakeChatDataSource())
        val userDataSource = FakeUserDataSource(listOf(UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST)))
        val userRepository = UserRepository(FakeAuthDataSource("psych1"), userDataSource, FakeFavoriteDataSource())
        val chatRepository = ChatRepository(chatSource, FakeMessageDataSource(), FakeNoteDataSource())
        val vm = ChatViewModel(chatRepository, userRepository)

        val result = vm.openChatWith("pacjent1")
        advanceUntilIdle()

        assertNull(result)
    }

    @Test
    fun `saveNote zapisuje notatke z authorId zalogowanego uzytkownika`() = runTest(dispatcher) {
        val noteSource = FakeNoteDataSource()
        val vm = chatViewModelDlaPsychologa("psych1", noteSource = noteSource)

        vm.saveNote("czat-1", "Tytul", "Tresc notatki")
        advanceUntilIdle()

        val zapisana = noteSource.notes.value.single()
        assertEquals("psych1", zapisana.authorId)
        assertEquals("czat-1", zapisana.chatId)
        assertEquals("Tytul", zapisana.title)
        assertEquals("Tresc notatki", zapisana.content)
    }

    @Test
    fun `uczestnicy nie sa ladowani przed zalogowaniem i laduja sie po zalogowaniu`() = runTest(dispatcher) {
        val userDataSource = FakeUserDataSource(
            listOf(
                UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST),
                UserModel(id = "pacjent1", role = UserRole.PATIENT)
            )
        )
        val authDataSource = FakeAuthDataSource(initialUserId = null)
        val userRepository = UserRepository(authDataSource, userDataSource, FakeFavoriteDataSource())
        val chatRepository = ChatRepository(FakeChatDataSource(), FakeMessageDataSource(), FakeNoteDataSource())
        val vm = ChatViewModel(chatRepository, userRepository)

        val subskrypcja = launch { vm.participants.collect {} }
        advanceUntilIdle()

        assertEquals(0, userDataSource.getAllByRoleSubscriptionCount)
        assertEquals(emptyMap<String, UserModel>(), vm.participants.value)

        authDataSource.userId.value = "psych1"
        advanceUntilIdle()

        assertEquals(
            mapOf(
                "psych1" to UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST),
                "pacjent1" to UserModel(id = "pacjent1", role = UserRole.PATIENT)
            ),
            vm.participants.value
        )
        assertEquals(3, userDataSource.getAllByRoleSubscriptionCount)

        subskrypcja.cancel()
    }

    @Test
    fun `deleteNote usuwa notatke ze zrodla`() = runTest(dispatcher) {
        val noteSource = FakeNoteDataSource()
        noteSource.notes.value = listOf(
            NoteModel(noteId = "notatka-1", chatId = "czat-1", authorId = "psych1", title = "T", content = "C")
        )
        val vm = chatViewModelDlaPsychologa("psych1", noteSource = noteSource)

        vm.deleteNote("notatka-1")
        advanceUntilIdle()

        assertEquals(emptyList<NoteModel>(), noteSource.notes.value)
    }
}
