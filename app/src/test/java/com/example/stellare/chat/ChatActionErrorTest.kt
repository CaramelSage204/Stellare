package com.example.stellare.chat

import com.example.stellare.data.model.MessageModel
import com.example.stellare.data.model.NoteModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.remote.MessageDataSource
import com.example.stellare.data.remote.NoteDataSource
import com.example.stellare.data.repository.ChatRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeAuthDataSource
import com.example.stellare.fakes.FakeChatDataSource
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeMessageDataSource
import com.example.stellare.fakes.FakeNoteDataSource
import com.example.stellare.fakes.FakeRatingDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.fakes.privateProfilesOf
import com.example.stellare.ui.chat.ChatViewModel
import com.example.stellare.ui.chat.NOTE_DELETE_FAILED_MESSAGE
import com.example.stellare.ui.chat.NOTE_SAVE_FAILED_MESSAGE
import com.example.stellare.ui.chat.SEND_FAILED_MESSAGE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

private class OdmawiajaceWiadomosci(delegate: MessageDataSource) : MessageDataSource by delegate {
    override suspend fun saveMessage(message: MessageModel) {
        throw IllegalStateException("PERMISSION_DENIED: Missing or insufficient permissions.")
    }
}

private class OdmawiajaceNotatki(delegate: NoteDataSource) : NoteDataSource by delegate {
    override suspend fun saveNote(note: NoteModel) {
        throw IllegalStateException("PERMISSION_DENIED: Missing or insufficient permissions.")
    }

    override suspend fun deleteNote(noteId: String) {
        throw IllegalStateException("PERMISSION_DENIED: Missing or insufficient permissions.")
    }
}

/**
 * Zmierzone na urzadzeniu: po odmowie regul pole tekstowe zostawalo puste, rozmowa bez nowego
 * dymka, a na ekranie nie bylo ani slowa - wpisana tresc znikala i nikt nie wiedzial, ze
 * wiadomosc nie wyszla. Komunikat mowi o stanie tu i teraz; niczego nie obiecuje na przyszlosc.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatActionErrorTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        messages: MessageDataSource = FakeMessageDataSource(),
        notes: NoteDataSource = FakeNoteDataSource()
    ): ChatViewModel {
        val users = FakeUserDataSource(listOf(UserModel(id = "psych1", role = UserRole.PSYCHOLOGIST)))
        val userRepository = UserRepository(
            FakeAuthDataSource("psych1"), users, FakeFavoriteDataSource(), FakeRatingDataSource(), privateProfilesOf(users)
        )
        return ChatViewModel(ChatRepository(FakeChatDataSource(), messages, notes), userRepository)
    }

    @Test
    fun `nieudane wyslanie wiadomosci mowi o sobie`() = runTest(dispatcher) {
        val vm = viewModel(messages = OdmawiajaceWiadomosci(FakeMessageDataSource()))

        vm.send("czat-1", "wiadomosc, ktora nie wyszla")
        advanceUntilIdle()

        assertEquals(
            "wiadomosci nie ma w rozmowie, a pole tekstowe juz sie wyczyscilo: bez komunikatu " +
                "uzytkownik jest przekonany, ze wyslal",
            SEND_FAILED_MESSAGE,
            vm.actionError.value
        )
    }

    @Test
    fun `nieudany zapis notatki mowi o sobie`() = runTest(dispatcher) {
        val vm = viewModel(notes = OdmawiajaceNotatki(FakeNoteDataSource()))

        vm.saveNote("czat-1", "Sesja 2", "Wnioski z sesji")
        advanceUntilIdle()

        assertEquals(
            "formularz notatki zamyka sie od razu po kliknieciu, wiec bez komunikatu notatka " +
                "znika bez sladu razem z trescia, ktora psycholog wpisal",
            NOTE_SAVE_FAILED_MESSAGE,
            vm.actionError.value
        )
    }

    @Test
    fun `nieudane usuniecie notatki mowi o sobie`() = runTest(dispatcher) {
        val vm = viewModel(notes = OdmawiajaceNotatki(FakeNoteDataSource()))

        vm.deleteNote("notatka-1")
        advanceUntilIdle()

        assertEquals(
            "notatka zostaje na liscie, wiec bez komunikatu wyglada to jak przycisk, ktory nie dziala",
            NOTE_DELETE_FAILED_MESSAGE,
            vm.actionError.value
        )
    }

    @Test
    fun `udana wysylka nie zostawia komunikatu`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.send("czat-1", "pierwsza")
        advanceUntilIdle()

        assertNull("komunikat o awarii po udanej operacji bylby trzecia nieprawda tej samej klasy", vm.actionError.value)
    }
}
