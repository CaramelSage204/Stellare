package com.example.stellare.chat

import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.data.model.ChatModel
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelClockTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        clock: () -> Long,
        chatSource: FakeChatDataSource = FakeChatDataSource(),
        messageSource: FakeMessageDataSource = FakeMessageDataSource()
    ): ChatViewModel {
        val users = FakeUserDataSource(listOf(UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST)))
        val userRepository = UserRepository(FakeAuthDataSource("psych1"), users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users))
        return ChatViewModel(ChatRepository(chatSource, messageSource, FakeNoteDataSource()), userRepository, clock)
    }

    @Test
    fun `nowMillis zwraca czas z wstrzyknietego zegara`() {
        assertEquals("ekrany maja dostawac czas od zegara viewmodelu", 4_242L, viewModel({ 4_242L }).nowMillis())
    }

    @Test
    fun `wyslana wiadomosc i ostatnia wiadomosc czatu dostaja czas z zegara`() = runTest(dispatcher) {
        val chatSource = FakeChatDataSource()
        chatSource.chats.value = listOf(
            ChatModel(chatId = "czat-1", psychologistId = "psych1", patientId = "pacjent1", participants = listOf("psych1", "pacjent1"))
        )
        val messageSource = FakeMessageDataSource()
        val vm = viewModel({ 777L }, chatSource, messageSource)

        vm.send("czat-1", "czesc")
        advanceUntilIdle()

        assertEquals("znacznik wiadomosci z zegara", 777L, messageSource.messages.value.single().timestamp)
        assertEquals("lastMessageTime z zegara", 777L, chatSource.chats.value.single().lastMessageTime)
    }

    @Test
    fun `nowy czat dostaje czas utworzenia z zegara`() = runTest(dispatcher) {
        val chatSource = FakeChatDataSource()
        val vm = viewModel({ 555L }, chatSource)

        vm.openChatWith("pacjent1")
        advanceUntilIdle()

        assertEquals("lastMessageTime nowego czatu z zegara", 555L, chatSource.chats.value.single().lastMessageTime)
    }
}
