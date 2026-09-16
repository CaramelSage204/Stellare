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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chats: StateFlow<List<ChatModel>> = currentUser
        .flatMapLatest { user ->
            if (user != null) {
                chatRepository.getChatsForUserFlow(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val participants: StateFlow<Map<String, UserModel>> = currentUser
        .flatMapLatest { user ->
            if (user != null) {
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

    fun messagesFor(chatId: String): Flow<List<MessageModel>> = chatRepository.getMessagesForChatFlow(chatId)

    fun notesFor(chatId: String): Flow<List<NoteModel>> = chatRepository.getNotesForChatFlow(chatId)

    fun send(chatId: String, text: String) {
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser() ?: return@launch
                val msg = MessageModel(
                    chatId = chatId,
                    senderId = user.id,
                    text = text,
                    timestamp = System.currentTimeMillis()
                )
                chatRepository.insertMessage(msg)

                val chat = chatRepository.getChatsForUserFlow(user.id).first().find { it.chatId == chatId }
                if (chat != null) {
                    chatRepository.updateChat(chat.copy(lastMessage = text, lastMessageTime = System.currentTimeMillis()))
                }
            } catch (e: Exception) {
                Log.e("Chat", "Nie udalo sie wyslac wiadomosci", e)
            }
        }
    }

    fun saveNote(chatId: String, title: String, content: String) {
        viewModelScope.launch {
            val authorId = userRepository.getCurrentUser()?.id ?: return@launch
            try {
                val note = NoteModel(chatId = chatId, authorId = authorId, title = title, content = content)
                chatRepository.insertNote(note)
            } catch (e: Exception) {
                Log.e("Notes", "Nie udalo sie zapisac notatki", e)
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            try {
                chatRepository.deleteNote(noteId)
            } catch (e: Exception) {
                Log.e("Notes", "Nie udalo sie usunac notatki", e)
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
                    lastMessageTime = System.currentTimeMillis()
                )
            )
            return nowyId
        } catch (e: Exception) {
            Log.e("Chat", "Nie udalo sie otworzyc czatu", e)
            return null
        }
    }
}
