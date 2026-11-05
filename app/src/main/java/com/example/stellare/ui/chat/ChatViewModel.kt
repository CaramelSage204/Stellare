package com.example.stellare.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.ChatModel
import com.example.stellare.data.model.MessageModel
import com.example.stellare.data.model.NoteModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.ChatRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.ui.UserFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal const val SEND_FAILED_MESSAGE =
    "Nie udało się wysłać wiadomości. Nie ma jej w rozmowie. Spróbuj ponownie."
internal const val NOTE_SAVE_FAILED_MESSAGE =
    "Nie udało się zapisać notatki. Nie ma jej na liście. Spróbuj ponownie."
internal const val NOTE_DELETE_FAILED_MESSAGE =
    "Nie udało się usunąć notatki. Nadal jest na liście. Spróbuj ponownie."
internal const val CHAT_NOT_SIGNED_IN_MESSAGE = "Brak zalogowanego użytkownika."

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    fun nowMillis(): Long = clock()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun clearActionError() {
        _actionError.value = null
    }

    val chats: StateFlow<List<ChatModel>> = userRepository
        .signedInUserIdFlow()
        .flatMapLatest { uid ->
            if (uid != null) chatRepository.getChatsForUserFlow(uid) else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val participants: StateFlow<Map<String, UserModel>> = userRepository
        .signedInUserIdFlow()
        .flatMapLatest { uid ->
            if (uid != null) {
                combine(
                    userRepository.getUsersOfRoleFlow(UserRole.PSYCHOLOGIST),
                    userRepository.getUsersOfRoleFlow(UserRole.PSYCHOLOGY_STUDENT),
                    userRepository.getUsersOfRoleFlow(UserRole.PATIENT)
                ) { psychs, students, patients ->
                    (psychs + students + patients).associateBy { it.id }
                }
            } else {
                flowOf(emptyMap())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun messagesFor(chatId: String): Flow<List<MessageModel>> =
        userRepository.whileSignedIn(emptyList()) { chatRepository.getMessagesForChatFlow(chatId) }

    fun notesFor(chatId: String): Flow<List<NoteModel>> =
        combine(userRepository.signedInUserIdFlow(), chats) { uid, allChats ->
            canSeeNotesTab(allChats.find { it.chatId == chatId }, uid)
        }
            .distinctUntilChanged()
            .flatMapLatest { allowed ->
                if (allowed) chatRepository.getNotesForChatFlow(chatId) else flowOf(emptyList())
            }

    fun send(chatId: String, text: String) {
        _actionError.value = null
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser()
                if (user == null) {
                    _actionError.value = CHAT_NOT_SIGNED_IN_MESSAGE
                    return@launch
                }
                val msg = MessageModel(
                    chatId = chatId,
                    senderId = user.id,
                    text = text,
                    timestamp = clock()
                )
                chatRepository.insertMessage(msg)

                val chat = chatRepository.getChatsForUserFlow(user.id).first().find { it.chatId == chatId }
                if (chat != null) {
                    chatRepository.updateChat(chat.copy(lastMessage = text, lastMessageTime = clock()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Chat", "Nie udalo sie wyslac wiadomosci", e)
                _actionError.value = UserFacingError.message(e, SEND_FAILED_MESSAGE)
            }
        }
    }

    fun saveNote(chatId: String, title: String, content: String) {
        _actionError.value = null
        viewModelScope.launch {
            try {
                val authorId = userRepository.getCurrentUser()?.id
                if (authorId == null) {
                    _actionError.value = CHAT_NOT_SIGNED_IN_MESSAGE
                    return@launch
                }
                val note = NoteModel(chatId = chatId, authorId = authorId, title = title, content = content)
                chatRepository.insertNote(note)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Notes", "Nie udalo sie zapisac notatki", e)
                _actionError.value = UserFacingError.message(e, NOTE_SAVE_FAILED_MESSAGE)
            }
        }
    }

    fun deleteNote(noteId: String) {
        _actionError.value = null
        viewModelScope.launch {
            try {
                chatRepository.deleteNote(noteId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Notes", "Nie udalo sie usunac notatki", e)
                _actionError.value = UserFacingError.message(e, NOTE_DELETE_FAILED_MESSAGE)
            }
        }
    }

    suspend fun openChatWith(otherUserId: String): String? {
        try {
            val current = userRepository.getCurrentUser() ?: return null
            val jestPsychologiem = current.role == UserRole.PSYCHOLOGIST ||
                current.role == UserRole.PSYCHOLOGY_STUDENT
            val psychologistId = if (jestPsychologiem) current.id else otherUserId
            val patientId = if (jestPsychologiem) otherUserId else current.id

            chatRepository.getChatByParticipants(psychologistId, patientId)?.let { return it.chatId }

            val nowyId = chatRepository.newChatId()
            chatRepository.createChat(
                ChatModel(
                    chatId = nowyId,
                    psychologistId = psychologistId,
                    patientId = patientId,
                    participants = listOf(psychologistId, patientId),
                    lastMessage = "Rozpoczęto nowy czat.",
                    lastMessageTime = clock()
                )
            )
            return nowyId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Chat", "Nie udalo sie otworzyc czatu", e)
            return null
        }
    }
}
