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
class ChatViewModelSignedInWithoutDocumentTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val czat = ChatModel(
        chatId = "c1",
        psychologistId = "p1",
        patientId = "k1",
        participants = listOf("p1", "k1"),
        lastMessage = "Czesc",
        lastMessageTime = 1L
    )

    private fun zbuduj(signedInAs: String?): ChatViewModel {
        val users = FakeUserDataSource(listOf(UserModel(id = "p1", role = UserRole.PSYCHOLOGIST)))
        val chats = FakeChatDataSource().also { it.chats.value = listOf(czat) }
        val userRepository = UserRepository(FakeAuthDataSource(signedInAs), users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users))
        return ChatViewModel(ChatRepository(chats, FakeMessageDataSource(), FakeNoteDataSource()), userRepository)
    }

    @Test
    fun `zalogowany bez dokumentu uzytkownika dostaje swoje czaty`() = runTest(dispatcher) {
        val vm = zbuduj(signedInAs = "k1")
        backgroundScope.launch { vm.chats.collect {} }
        runCurrent()

        assertEquals(listOf("c1"), vm.chats.value.map { it.chatId })
    }

    @Test
    fun `niezalogowany nie dostaje czatow`() = runTest(dispatcher) {
        val vm = zbuduj(signedInAs = null)
        backgroundScope.launch { vm.chats.collect {} }
        runCurrent()

        assertEquals(emptyList<ChatModel>(), vm.chats.value)
    }

    @Test
    fun `zalogowany bez dokumentu uzytkownika dostaje uczestnikow`() = runTest(dispatcher) {
        val vm = zbuduj(signedInAs = "k1")
        backgroundScope.launch { vm.participants.collect {} }
        runCurrent()

        assertEquals(setOf("p1"), vm.participants.value.keys)
    }

    @Test
    fun `niezalogowany nie dostaje uczestnikow`() = runTest(dispatcher) {
        val vm = zbuduj(signedInAs = null)
        backgroundScope.launch { vm.participants.collect {} }
        runCurrent()

        assertEquals(emptySet<String>(), vm.participants.value.keys)
    }
}
